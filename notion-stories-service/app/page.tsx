import Link from 'next/link'
import { ArrowUpRight, Home } from 'lucide-react'
import { StoryCard } from '../components/StoryCard'
import { getConfig } from '../lib/config'
import { listStories } from '../lib/notion-official'

export default async function HomePage() {
  const config = getConfig()
  const result = await listStories()

  return (
    <main className="site-shell">
      <header className="site-header">
        <div>
          <p className="eyebrow">Notion Stories</p>
          <h1>{config.siteTitle}</h1>
          <p>{config.siteDescription}</p>
        </div>
        <nav aria-label="站点导航">
          <a href={config.haloSiteUrl}>
            <Home aria-hidden="true" size={18} />
            主站
          </a>
          <a href={`${config.haloSiteUrl}/photos`}>
            图库
            <ArrowUpRight aria-hidden="true" size={18} />
          </a>
        </nav>
      </header>

      {result.configMissing ? (
        <section className="empty-state">
          <h2>服务尚未完成配置</h2>
          <p>请在服务器的 .env 中填写 NOTION_TOKEN 和 NOTION_DATABASE_ID。</p>
        </section>
      ) : result.stories.length === 0 ? (
        <section className="empty-state">
          <h2>还没有公开的图片故事</h2>
          <p>请确认 Notion 数据库里的发布字段已开启，并且 Integration 已连接到该数据库。</p>
        </section>
      ) : (
        <section className="story-grid" aria-label="图片故事列表">
          {result.stories.map((story) => (
            <StoryCard key={story.pageId} story={story} />
          ))}
        </section>
      )}

      <footer className="site-footer">
        <Link href={config.haloSiteUrl}>返回 xiongfan.me</Link>
      </footer>
    </main>
  )
}
