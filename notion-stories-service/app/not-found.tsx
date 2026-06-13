import Link from 'next/link'

export default function NotFound() {
  return (
    <main className="site-shell">
      <section className="empty-state">
        <h1>没有找到这篇图片故事</h1>
        <p>请检查链接是否正确，或确认 Notion 数据库中这篇内容仍然处于公开状态。</p>
        <Link href="/">返回图片故事</Link>
      </section>
    </main>
  )
}
