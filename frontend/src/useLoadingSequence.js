import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  FINALIZING_PHASE,
  HINT_INTERVAL_MS,
  LOADING_PHASES,
  OVERTIME_AFTER_SECONDS,
  OVERTIME_HINTS,
} from './loadingMessages'

const LAST_INDEX = LOADING_PHASES.length - 1

/** 로딩 패널이 한 프레임만 번쩍이는 것을 막는 최소 표시 시간 */
const MIN_PANEL_MS = 800
/** 이 시간 안에 응답이 오면 완료 연출을 통째로 생략한다 */
const SKIP_SEQUENCE_MS = 3000
/** 성공 시 "결과 준비"를 보여 주는 시간 */
const FINALIZING_MS = 500
/** 실패 시 실패 단계를 보여 주는 시간 */
const FAILURE_MS = 800

const shuffle = (items) => {
  const bag = [...items]
  for (let index = bag.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1))
    ;[bag[index], bag[swapIndex]] = [bag[swapIndex], bag[index]]
  }
  return bag
}

/**
 * 분석 대기 화면의 단계·보조 문구·경과 시간을 계산한다.
 *
 * 시간 기반 시나리오이므로 서버의 실제 진행 상태는 알 수 없다. 따라서
 * - 단계 진입은 경과 시간(LOADING_PHASES[].startAt)으로만 판단하고,
 * - 오버타임 전환은 단계와 무관하게 경과 시간(OVERTIME_AFTER_SECONDS)으로만 판단하며,
 * - 응답이 오기 전에는 마지막 단계를 임의로 완료 표시하지 않는다.
 *
 * @param {boolean} loading 요청 진행 여부
 * @param {'success'|'partial'|'error'|null} outcome 응답 판정. null이면 아직 대기 중
 * @param {() => void} onSequenceComplete 연출이 끝나 화면을 전환해도 될 때 호출된다
 */
export function useLoadingSequence(loading, outcome, onSequenceComplete) {
  const [phaseId, setPhaseId] = useState(LOADING_PHASES[0].id)
  const [title, setTitle] = useState(LOADING_PHASES[0].title)
  const [hint, setHint] = useState(LOADING_PHASES[0].hints[0])
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [completedPhaseIds, setCompletedPhaseIds] = useState([])
  const [failedPhaseId, setFailedPhaseId] = useState(null)
  const [isFinalizing, setIsFinalizing] = useState(false)

  const startTimeRef = useRef(0)
  const phaseIndexRef = useRef(0)
  const bagRef = useRef([])
  const lastHintRef = useRef('')
  const overtimeRef = useRef(false)
  const completeRef = useRef(onSequenceComplete)

  useEffect(() => {
    completeRef.current = onSequenceComplete
  }, [onSequenceComplete])

  /** 셔플백: 배열을 섞어 대기열로 만들고 하나씩 소비한다. 경계에서 직전 문구와 겹치면 자리를 바꾼다. */
  const startHintBag = useCallback((hints) => {
    const bag = shuffle(hints)
    if (bag.length > 1 && bag[0] === lastHintRef.current) {
      ;[bag[0], bag[1]] = [bag[1], bag[0]]
    }
    const nextHint = bag.shift() || ''
    bagRef.current = bag
    lastHintRef.current = nextHint
    setHint(nextHint)
  }, [])

  const enterPhase = useCallback((index) => {
    const phase = LOADING_PHASES[index]
    phaseIndexRef.current = index
    setPhaseId(phase.id)
    setTitle(phase.title)
    startHintBag(overtimeRef.current ? OVERTIME_HINTS : phase.hints)
  }, [startHintBag])

  // 시작 / 정리
  useLayoutEffect(() => {
    if (!loading) {
      phaseIndexRef.current = 0
      overtimeRef.current = false
      bagRef.current = []
      lastHintRef.current = ''
      setPhaseId(LOADING_PHASES[0].id)
      setTitle(LOADING_PHASES[0].title)
      setHint(LOADING_PHASES[0].hints[0])
      setElapsedSeconds(0)
      setCompletedPhaseIds([])
      setFailedPhaseId(null)
      setIsFinalizing(false)
      return
    }

    startTimeRef.current = Date.now()
    overtimeRef.current = false
    lastHintRef.current = ''
    setElapsedSeconds(0)
    setCompletedPhaseIds([])
    setFailedPhaseId(null)
    setIsFinalizing(false)
    enterPhase(0)
  }, [loading, enterPhase])

  // 1초 티커 — 경과 시간, 단계 전환, 오버타임 전환
  useEffect(() => {
    if (!loading || outcome) return undefined

    const ticker = setInterval(() => {
      const elapsed = (Date.now() - startTimeRef.current) / 1000
      setElapsedSeconds(Math.floor(elapsed))

      let nextIndex = 0
      for (let index = LAST_INDEX; index >= 0; index -= 1) {
        if (elapsed >= LOADING_PHASES[index].startAt) {
          nextIndex = index
          break
        }
      }

      // 오버타임은 단계 진입과 별개로, 경과 시간만 보고 판단한다.
      if (elapsed >= OVERTIME_AFTER_SECONDS && !overtimeRef.current) {
        overtimeRef.current = true
        if (nextIndex === phaseIndexRef.current) {
          startHintBag(OVERTIME_HINTS)
        }
      }

      if (nextIndex !== phaseIndexRef.current) {
        setCompletedPhaseIds(LOADING_PHASES.slice(0, nextIndex).map((phase) => phase.id))
        enterPhase(nextIndex)
      }
    }, 1000)

    return () => clearInterval(ticker)
  }, [loading, outcome, enterPhase, startHintBag])

  // 보조 문구 순환
  useEffect(() => {
    if (!loading || outcome) return undefined

    const hintTimer = setInterval(() => {
      if (bagRef.current.length === 0) {
        startHintBag(
          overtimeRef.current ? OVERTIME_HINTS : LOADING_PHASES[phaseIndexRef.current].hints,
        )
        return
      }
      const nextHint = bagRef.current.shift()
      lastHintRef.current = nextHint
      setHint(nextHint)
    }, HINT_INTERVAL_MS)

    return () => clearInterval(hintTimer)
  }, [loading, outcome, phaseId, startHintBag])

  // 응답 도착 후 마무리
  useEffect(() => {
    if (!loading || !outcome) return undefined

    const elapsedMs = Date.now() - startTimeRef.current

    // 지나치게 빨리 끝난 경우(입력 검증 실패 등) 연출을 생략한다.
    if (elapsedMs < SKIP_SEQUENCE_MS) {
      const timer = setTimeout(() => completeRef.current(), Math.max(0, MIN_PANEL_MS - elapsedMs))
      return () => clearTimeout(timer)
    }

    // 실패: 도달하지 못한 단계에 완료 표시를 하지 않는다.
    if (outcome === 'error') {
      setFailedPhaseId(LOADING_PHASES[phaseIndexRef.current].id)
      const timer = setTimeout(() => completeRef.current(), FAILURE_MS)
      return () => clearTimeout(timer)
    }

    // 성공/부분 성공: After가 200이면 네 단계가 서버에서 실제로 모두 수행된 것이므로
    // 남은 단계를 순차 지연 없이 한 번에 완료 처리한다.
    setCompletedPhaseIds(LOADING_PHASES.map((phase) => phase.id))
    setFailedPhaseId(null)
    setIsFinalizing(true)
    phaseIndexRef.current = LAST_INDEX
    setPhaseId(FINALIZING_PHASE.id)
    setTitle(FINALIZING_PHASE.title)
    startHintBag(FINALIZING_PHASE.hints)
    const timer = setTimeout(() => completeRef.current(), FINALIZING_MS)
    return () => clearTimeout(timer)
  }, [loading, outcome, startHintBag])

  return {
    phaseId,
    title,
    hint,
    elapsedSeconds,
    completedPhaseIds,
    failedPhaseId,
    isFinalizing,
  }
}
