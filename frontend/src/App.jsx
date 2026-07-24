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

function App() {
  const [input, setInput] = useState('')
  const [result, setResult] = useState(null)
  const [screen, setScreen] = useState('input')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event) => {
    event.preventDefault()
    setLoading(true)
    setError('')
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 180_000)

    try {
      const response = await fetch('/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ input }),
        signal: controller.signal,
      })
      if (!response.ok) throw new Error(`요청에 실패했습니다. (${response.status})`)
      setResult(await response.json())
      setScreen('diagnosis')
    } catch (requestError) {
      setError(
        requestError.name === 'AbortError'
          ? '응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.'
          : requestError.message || '요청 중 오류가 발생했습니다.',
      )
    } finally {
      clearTimeout(timeout)
      setLoading(false)
    }
  }

  const download = () => {
    const text = result.scenes
      .map((scene, index) => `${index + 1}. ${scene.stage}\n\n${scene.text}`)
      .join('\n\n---\n\n')
    const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = 'storylens-outline.txt'
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <main className="min-h-screen bg-slate-950 px-4 py-10 text-slate-100">
      <div className="mx-auto max-w-4xl">
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
            {error && <p className="text-red-400" role="alert">{error}</p>}
            <button
              type="submit"
              disabled={loading}
              className="rounded bg-cyan-600 px-5 py-3 font-semibold disabled:opacity-50"
            >
              {loading ? '분석과 구조 생성을 진행 중입니다…' : 'StoryLens 분석 시작'}
            </button>
            {loading && <p className="text-sm text-slate-400" role="status">최대 몇 분이 걸릴 수 있습니다.</p>}
          </form>
        )}

        {screen === 'diagnosis' && result && (
          <section className="space-y-5">
            <h2 className="text-2xl font-bold">진단 결과</h2>
            <div className="grid gap-4 md:grid-cols-2">
              {result.diagnosis.tag_results.map((tag) => (
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
              생성 결과 보기
            </button>
          </section>
        )}

        {screen === 'result' && result && (
          <section className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-2xl font-bold">5단계 구조 생성 결과</h2>
              <button
                type="button"
                onClick={download}
                className="rounded bg-cyan-600 px-4 py-2 font-semibold"
              >
                텍스트 파일 다운로드
              </button>
            </div>

            <div className="space-y-4">
              {result.scenes.map((scene, index) => (
                <article key={scene.stage} className="rounded-lg bg-slate-900 p-6">
                  <h3 className="mb-3 text-xl font-semibold">
                    {index + 1}. {scene.stage}
                  </h3>
                  <p className="whitespace-pre-wrap leading-7 text-slate-300">{scene.text}</p>
                </article>
              ))}
            </div>

            <section className="rounded-lg border border-slate-700 p-6">
              <h3 className="mb-4 text-xl font-semibold">
                자체 검증: {result.verification.overall_pass_fail}
              </h3>
              <ul className="space-y-3">
                {result.verification.checklist.map((item) => (
                  <li key={item.item_number}>
                    <strong>{item.item_number}. {item.item} — {item.pass_fail}</strong>
                    <p className="text-sm text-slate-400">{item.evidence}</p>
                  </li>
                ))}
              </ul>
            </section>

            <button
              type="button"
              onClick={() => {
                setResult(null)
                setScreen('input')
              }}
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
