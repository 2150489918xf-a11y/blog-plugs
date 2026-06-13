import Link from 'next/link'
import { CalendarDays } from 'lucide-react'
import type { StorySummary } from '../lib/notion-official'

type StoryCardProps = {
  story: StorySummary
}

export function StoryCard({ story }: StoryCardProps) {
  return (
    <article className="story-card">
      <Link href={`/${encodeURIComponent(story.slug)}`} className="story-card-link">
        <span className="story-card-media">
          {story.coverUrl ? <img src={story.coverUrl} alt="" loading="lazy" /> : null}
        </span>
        <span className="story-card-body">
          {story.date ? (
            <span className="story-card-date">
              <CalendarDays aria-hidden="true" size={16} />
              <time dateTime={story.date}>{story.date}</time>
            </span>
          ) : null}
          <strong>{story.title}</strong>
          {story.summary ? <span className="story-card-summary">{story.summary}</span> : null}
          {story.tags.length > 0 ? (
            <span className="tag-row">
              {story.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </span>
          ) : null}
        </span>
      </Link>
    </article>
  )
}
