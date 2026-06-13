import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { ArrowLeft, ExternalLink, Home } from 'lucide-react'
import { NotionPage } from '../../components/NotionPage'
import { getConfig } from '../../lib/config'
import { getStoryBySlug } from '../../lib/notion-official'
import { getNotionRecordMap } from '../../lib/notion-x'

type PageProps = {
  params: {
    slug: string
  }
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const story = await getStoryBySlug(decodeURIComponent(params.slug))
  if (!story) {
    return {
      title: '未找到图片故事'
    }
  }
  return {
    title: story.title,
    description: story.summary || story.title
  }
}

export default async function StoryDetailPage({ params }: PageProps) {
  const config = getConfig()
  const slug = decodeURIComponent(params.slug)
  const story = await getStoryBySlug(slug)

  if (!story) {
    notFound()
  }

  const recordMap = await getNotionRecordMap(story.pageId)

  return (
    <main className="detail-shell">
      <header className="detail-topbar">
        <Link href="/" className="topbar-link">
          <ArrowLeft aria-hidden="true" size={18} />
          图片故事
        </Link>
        <nav aria-label="站点导航">
          <a href={config.haloSiteUrl} className="topbar-link">
            <Home aria-hidden="true" size={18} />
            主站
          </a>
          {story.notionUrl ? (
            <a href={story.notionUrl} className="topbar-link" target="_blank" rel="noreferrer">
              Notion
              <ExternalLink aria-hidden="true" size={16} />
            </a>
          ) : null}
        </nav>
      </header>

      <article className="notion-article">
        <NotionPage pageId={story.pageId} recordMap={recordMap} />
      </article>
    </main>
  )
}
