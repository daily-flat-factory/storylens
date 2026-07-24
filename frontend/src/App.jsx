import { useState } from 'react'
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'

const TAG_NAMES = {
  regret_retry: '후회/재도전',
  revenge_retribution: '복수/응징',
  disguise_hidden_identity: '신분위장/정체성은닉',
  unfamiliar_world_adaptation: '낯선세계 적응',
  informational_advantage_twist: '정보우위/반전',
  social_status_reversal: '사회적 지위 역전',
  heroic_trial_sacrifice: '영웅서사/시련과 희생',
  metacognitive_dual_perspective: '메타인지/이중시선',
  identity_ambiguity: '정체성 모호성',
}

const requestGeneration = async (path, input, signal) => {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ input }),
    signal,
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `요청에 실패했습니다. (${response.status})`)
  }
  return response.json()
}

const failureMessage = (outcome) =>
  outcome.reason?.name === 'AbortError'
    ? '응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.'
    : outcome.reason?.message || '요청 중 오류가 발생했습니다.'

const strengthLevel = (strength) =>
  strength?.startsWith('강하게') ? 3 : strength?.startsWith('중간') ? 2 : strength?.startsWith('약하게') ? 1 : 0

function App() {
  const reduceMotion = useReducedMotion()
  const [input, setInput] = useState('')
  const [afterResult, setAfterResult] = useState(null)
  const [beforeResult, setBeforeResult] = useState(null)
  const [errors, setErrors] = useState({ before: '', after: '' })
  const [screen, setScreen] = useState('input')
  const [loading, setLoading] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setLoading(true)
    setErrors({ before: '', after: '' })
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 180_000)

    try {
      const [after, before] = await Promise.allSettled([
        requestGeneration('/api/generate', input, controller.signal),
        requestGeneration('/api/generate-naive', input, controller.signal),
      ])
      const afterValue = after.status === 'fulfilled' ? after.value : null
      const beforeValue = before.status === 'fulfilled' ? before.value : null
      setAfterResult(afterValue)
      setBeforeResult(beforeValue)
      setErrors({
        after: after.status === 'rejected' ? failureMessage(after) : '',
        before: before.status === 'rejected' ? failureMessage(before) : '',
      })
      setScreen(afterValue ? 'diagnosis' : 'result')
    } finally {
      clearTimeout(timeout)
      setLoading(false)
    }
  }

  const download = () => {
    const text = afterResult.scenes
      .map((scene, index) => `${index + 1}. ${scene.stage}\n\n${scene.text}`)
      .join('\n\n---\n\n')
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = 'storylens-outline.txt'
    link.click()
    URL.revokeObjectURL(url)
  }

  const reset = () => {
    setAfterResult(null)
    setBeforeResult(null)
    setErrors({ before: '', after: '' })
    setScreen('input')
  }

  const screenMotion = {
    initial: { opacity: 0, y: reduceMotion ? 0 : 16 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: reduceMotion ? 0 : -10 },
    transition: { duration: reduceMotion ? 0 : 0.35 },
  }
  const listMotion = {
    animate: { transition: { staggerChildren: reduceMotion ? 0 : 0.09 } },
  }
  const itemMotion = {
    initial: { opacity: 0, y: reduceMotion ? 0 : 14 },
    animate: { opacity: 1, y: 0 },
  }

  return (
    <main className="app-shell">
      <div className="ambient-glow ambient-glow-left" />
      <div className="ambient-glow ambient-glow-right" />
      <div className="app-frame">
        <header className="app-header">
          <div>
            <p className="eyebrow"><span>◆</span> 웹소설 창작 가드레일</p>
            <h1>Story<span>Lens</span></h1>
          </div>
          <div className="system-badge" aria-label="시스템 준비 완료">
            <span className="status-dot" />
            SYSTEM READY
          </div>
        </header>

        <AnimatePresence mode="wait">
          {screen === 'input' && (
            <motion.form key="input" onSubmit={submit} className="input-panel" {...screenMotion}>
              <div className="panel-heading">
                <span className="panel-number">01</span>
                <div>
                  <p>STORY SCAN</p>
                  <h2>이야기 설정을 입력하세요</h2>
                </div>
              </div>
              <label htmlFor="story-input" className="sr-only">이야기 설정</label>
              <div className="textarea-frame">
                <span className="corner corner-tl" />
                <span className="corner corner-tr" />
                <span className="corner corner-bl" />
                <span className="corner corner-br" />
                <textarea
                  id="story-input"
                  required
                  rows="10"
                  value={input}
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="주인공, 상황, 그리고 이루려는 목표를 입력하세요."
                />
                <span className="input-hint">INPUT / NARRATIVE PROFILE</span>
              </div>
              <button type="submit" disabled={loading} className="primary-button">
                {loading ? '분석 시퀀스 실행 중' : 'StoryLens 비교 시작'}
                <span aria-hidden="true">{loading ? '···' : '→'}</span>
              </button>
              {loading && (
                <motion.div
                  className="loading-panel"
                  role="status"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                >
                  <motion.div
                    className="loader-orbit"
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1.8, repeat: Infinity, ease: 'linear' }}
                  >
                    <span />
                  </motion.div>
                  <div>
                    <strong>트로프 신호를 추적하고 있습니다</strong>
                    <p>Before와 After를 함께 생성하므로 최대 몇 분이 걸릴 수 있습니다.</p>
                    <div className="scan-line"><motion.span animate={{ x: ['-100%', '380%'] }} transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }} /></div>
                  </div>
                </motion.div>
              )}
            </motion.form>
          )}

          {screen === 'diagnosis' && afterResult && (
            <motion.section key="diagnosis" className="diagnosis-screen" {...screenMotion}>
              <div className="screen-title">
                <div>
                  <p className="eyebrow"><span>◆</span> TROPE ANALYSIS COMPLETE</p>
                  <h2>서사 상태창</h2>
                </div>
                <span className="result-count">09 TAGS SCANNED</span>
              </div>

              <div className="status-window">
                <div className="status-window-top">
                  <span>STORYLENS / NARRATIVE STATUS</span>
                  <span>ACTIVE SIGNALS HIGHLIGHTED</span>
                </div>
                <motion.div className="tag-grid" variants={listMotion} initial="initial" animate="animate">
                  {afterResult.diagnosis.tag_results.map((tag, index) => {
                    const strength = tag.is_active ? tag.display_strength : '미감지'
                    const level = strengthLevel(strength)
                    return (
                      <motion.article
                        key={tag.tag_id}
                        className={`tag-card ${tag.is_active ? 'tag-active' : 'tag-inactive'}`}
                        variants={itemMotion}
                        transition={{ duration: 0.35 }}
                      >
                        <div className="tag-index">{String(index + 1).padStart(2, '0')}</div>
                        <div className="tag-body">
                          <div className="tag-heading">
                            <h3>{TAG_NAMES[tag.tag_id] || tag.tag_id}</h3>
                            <span className="tag-state">{tag.is_active ? 'DETECTED' : 'DORMANT'}</span>
                          </div>
                          <div className="strength-row">
                            <span>{strength}</span>
                            <div className="strength-gauge" aria-label={`감지 강도: ${strength}`}>
                              {[1, 2, 3].map((step) => <i key={step} className={step <= level ? 'filled' : ''} />)}
                            </div>
                          </div>
                          {tag.evidence.length > 0 && (
                            <ul className="evidence-list">
                              {tag.evidence.map((evidence) => <li key={evidence}>“{evidence}”</li>)}
                            </ul>
                          )}
                        </div>
                      </motion.article>
                    )
                  })}
                </motion.div>
              </div>
              <button type="button" onClick={() => setScreen('result')} className="primary-button next-button">
                Before / After 비교 보기 <span aria-hidden="true">→</span>
              </button>
            </motion.section>
          )}

          {screen === 'result' && (
            <motion.section key="result" className="comparison-screen" {...screenMotion}>
              <div className="screen-title comparison-heading">
                <div>
                  <p className="eyebrow"><span>◆</span> NARRATIVE COMPARISON</p>
                  <h2>Before / After</h2>
                </div>
                {afterResult && (
                  <button type="button" onClick={download} className="secondary-button">
                    After 텍스트 다운로드 <span aria-hidden="true">↓</span>
                  </button>
                )}
              </div>

              <div className="comparison-grid">
                <motion.section
                  aria-labelledby="before-title"
                  className="story-column before-column"
                  variants={listMotion}
                  initial="initial"
                  animate="animate"
                >
                  <motion.div className="column-header" variants={itemMotion}>
                    <span className="column-mark">B</span>
                    <div>
                      <h3 id="before-title">Before <small>일반 생성</small></h3>
                      <p>구조 가드레일과 자체 검증 없음</p>
                    </div>
                  </motion.div>
                  {errors.before && <p className="error-message" role="alert">{errors.before}</p>}
                  {beforeResult?.scenes.map((scene, index) => (
                    <motion.article key={scene.label} className="scene-card" variants={itemMotion}>
                      <span className="scene-number">{String(index + 1).padStart(2, '0')}</span>
                      <h4>{scene.label}</h4>
                      <p>{scene.text}</p>
                    </motion.article>
                  ))}
                </motion.section>

                <motion.section
                  aria-labelledby="after-title"
                  className="story-column after-column"
                  variants={listMotion}
                  initial="initial"
                  animate="animate"
                >
                  <motion.div className="column-header" variants={itemMotion}>
                    <span className="column-mark">A</span>
                    <div>
                      <h3 id="after-title">After <small>StoryLens</small></h3>
                      <p>구조 진단 · 고정 조건 · 자체 검증 적용</p>
                    </div>
                    <span className="guardrail-badge">GUARDRAIL ON</span>
                  </motion.div>
                  {errors.after && <p className="error-message" role="alert">{errors.after}</p>}
                  {afterResult?.scenes.map((scene, index) => (
                    <motion.article key={scene.stage} className="scene-card" variants={itemMotion}>
                      <span className="scene-number">{String(index + 1).padStart(2, '0')}</span>
                      <h4>{scene.stage}</h4>
                      <p>{scene.text}</p>
                    </motion.article>
                  ))}

                  {afterResult && (
                    <motion.section className="verification-panel" variants={itemMotion}>
                      <div className="verification-heading">
                        <div>
                          <span>ACTOR–EVALUATOR</span>
                          <h4>가드레일 자체 검증</h4>
                        </div>
                        <strong className={afterResult.verification.overall_pass_fail === 'PASS' ? 'pass' : 'fail'}>
                          {afterResult.verification.overall_pass_fail === 'PASS' ? '✓' : '✕'} {afterResult.verification.overall_pass_fail}
                        </strong>
                      </div>
                      <motion.ul variants={listMotion}>
                        {afterResult.verification.checklist.map((item) => {
                          const passed = item.pass_fail === 'PASS'
                          return (
                            <motion.li key={item.item_number} variants={itemMotion}>
                              <span className={`check-icon ${passed ? 'pass' : 'fail'}`} aria-label={item.pass_fail}>
                                {passed ? '✓' : '✕'}
                              </span>
                              <div>
                                <strong>{String(item.item_number).padStart(2, '0')} · {item.item}</strong>
                                <p>{item.evidence}</p>
                              </div>
                            </motion.li>
                          )
                        })}
                      </motion.ul>
                    </motion.section>
                  )}
                </motion.section>
              </div>

              <button type="button" onClick={reset} className="secondary-button reset-button">
                ← 새 설정 분석하기
              </button>
            </motion.section>
          )}
        </AnimatePresence>
      </div>
    </main>
  )
}

export default App
