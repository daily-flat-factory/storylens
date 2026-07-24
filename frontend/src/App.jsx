import { useState } from 'react'

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
  if (!response.ok) throw new Error(`요청에 실패했습니다. (${response.status})`)
  return response.json()
}

const failureMessage = (outcome) =>
  outcome.reason?.name === 'AbortError'
    ? '응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.'
    : outcome.reason?.message || '요청 중 오류가 발생했습니다.'

function App() {
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

  return (
    <main className="min-h-screen bg-slate-950 px-4 py-10 text-slate-100">
      <div className="mx-auto max-w-7xl">
        <header className="mb-8">
          <p className="text-sm font-semibold text-cyan-400">웹소설 창작 가드레일</p>
          <h1 className="text-3xl font-bold">StoryLens</h1>
        </header>

        {screen === 'input' && (
          <form onSubmit={submit} className="space-y-4 rounded-lg bg-slate-900 p-6">
            <label htmlFor="story-input" className="block text-lg font-semibold">
              이야기 설정
            </label>
            <textarea
              id="story-input"
              required
              rows="10"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="분석할 웹소설 설정을 입력하세요."
              className="w-full rounded border border-slate-700 bg-slate-950 p-4"
            />
            <button
              type="submit"
              disabled={loading}
              className="rounded bg-cyan-600 px-5 py-3 font-semibold disabled:opacity-50"
            >
              {loading ? 'Before와 After를 동시에 생성 중입니다…' : 'StoryLens 비교 시작'}
            </button>
            {loading && (
              <p className="text-sm text-slate-400" role="status">
                두 결과를 함께 생성하므로 최대 몇 분이 걸릴 수 있습니다.
              </p>
            )}
          </form>
        )}

        {screen === 'diagnosis' && afterResult && (
          <section className="space-y-5">
            <h2 className="text-2xl font-bold">진단 결과</h2>
            <div className="grid gap-4 md:grid-cols-2">
              {afterResult.diagnosis.tag_results.map((tag) => (
                <article key={tag.tag_id} className="rounded-lg bg-slate-900 p-5">
                  <div className="mb-3 flex items-center justify-between gap-3">
                    <h3 className="font-semibold">{TAG_NAMES[tag.tag_id] || tag.tag_id}</h3>
                    <span className="text-sm text-cyan-300">
                      {tag.display_strength || '미감지'}
                    </span>
                  </div>
                  <ul className="list-disc space-y-1 pl-5 text-sm text-slate-300">
                    {tag.evidence.map((evidence) => <li key={evidence}>{evidence}</li>)}
                  </ul>
                </article>
              ))}
            </div>
            <button
              type="button"
              onClick={() => setScreen('result')}
              className="rounded bg-cyan-600 px-5 py-3 font-semibold"
            >
              Before/After 비교 보기
            </button>
          </section>
        )}

        {screen === 'result' && (
          <section className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-2xl font-bold">Before/After 비교</h2>
              {afterResult && (
                <button
                  type="button"
                  onClick={download}
                  className="rounded bg-cyan-600 px-4 py-2 font-semibold"
                >
                  After 텍스트 다운로드
                </button>
              )}
            </div>

            <div className="grid items-start gap-6 lg:grid-cols-2">
              <section aria-labelledby="before-title" className="space-y-4">
                <div className="rounded-lg border border-slate-700 p-4">
                  <h3 id="before-title" className="text-xl font-bold">Before · 일반 생성</h3>
                  <p className="text-sm text-slate-400">구조 가드레일과 자체 검증 없음</p>
                </div>
                {errors.before && <p className="text-red-400" role="alert">{errors.before}</p>}
                {beforeResult?.scenes.map((scene) => (
                  <article key={scene.label} className="rounded-lg bg-slate-900 p-5">
                    <h4 className="mb-3 text-lg font-semibold">{scene.label}</h4>
                    <p className="whitespace-pre-wrap leading-7 text-slate-300">{scene.text}</p>
                  </article>
                ))}
              </section>

              <section aria-labelledby="after-title" className="space-y-4">
                <div className="rounded-lg border border-cyan-700 p-4">
                  <h3 id="after-title" className="text-xl font-bold">After · StoryLens</h3>
                  <p className="text-sm text-slate-400">구조 진단·고정 조건·자체 검증 적용</p>
                </div>
                {errors.after && <p className="text-red-400" role="alert">{errors.after}</p>}
                {afterResult?.scenes.map((scene, index) => (
                  <article key={scene.stage} className="rounded-lg bg-slate-900 p-5">
                    <h4 className="mb-3 text-lg font-semibold">
                      {index + 1}. {scene.stage}
                    </h4>
                    <p className="whitespace-pre-wrap leading-7 text-slate-300">{scene.text}</p>
                  </article>
                ))}

                {afterResult && (
                  <section className="rounded-lg border border-slate-700 p-5">
                    <h4 className="mb-4 text-lg font-semibold">
                      자체 검증: {afterResult.verification.overall_pass_fail}
                    </h4>
                    <ul className="space-y-3">
                      {afterResult.verification.checklist.map((item) => (
                        <li key={item.item_number}>
                          <strong>{item.item_number}. {item.item} — {item.pass_fail}</strong>
                          <p className="text-sm text-slate-400">{item.evidence}</p>
                        </li>
                      ))}
                    </ul>
                  </section>
                )}
              </section>
            </div>

            <button
              type="button"
              onClick={reset}
              className="rounded border border-slate-600 px-4 py-2"
            >
              새 설정 분석하기
            </button>
          </section>
        )}
      </div>
    </main>
  )
}

export default App
