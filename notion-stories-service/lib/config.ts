export type AppConfig = {
  notionToken: string
  databaseId: string
  siteUrl: string
  siteTitle: string
  siteDescription: string
  haloSiteUrl: string
  cacheTtlSeconds: number
  fields: {
    title: string
    slug: string
    date: string
    published: string
    publishedType: 'checkbox' | 'select' | 'status'
    publishedValue: string
    summary: string
    tags: string
    cover: string
  }
}

export function getConfig(): AppConfig {
  return {
    notionToken: env('NOTION_TOKEN'),
    databaseId: normalizeDatabaseId(env('NOTION_DATABASE_ID', '37bbedc8681180199bbce653a8bf2325')),
    siteUrl: trimTrailingSlash(env('SITE_URL', 'https://stories.xiongfan.me')),
    siteTitle: env('SITE_TITLE', '图片故事'),
    siteDescription: env('SITE_DESCRIPTION', '用 Notion 记录生活、照片和回忆。'),
    haloSiteUrl: trimTrailingSlash(env('HALO_SITE_URL', 'https://xiongfan.me')),
    cacheTtlSeconds: positiveInt(env('CACHE_TTL_SECONDS', '600'), 600),
    fields: {
      title: env('NOTION_TITLE_PROPERTY', '标题'),
      slug: env('NOTION_SLUG_PROPERTY', 'Slug'),
      date: env('NOTION_DATE_PROPERTY', '日期'),
      published: env('NOTION_PUBLISHED_PROPERTY', '状态'),
      publishedType: publishedType(env('NOTION_PUBLISHED_PROPERTY_TYPE', 'checkbox')),
      publishedValue: env('NOTION_PUBLISHED_VALUE', '公开'),
      summary: env('NOTION_SUMMARY_PROPERTY', '摘要'),
      tags: env('NOTION_TAGS_PROPERTY', '标签'),
      cover: env('NOTION_COVER_PROPERTY', '封面图')
    }
  }
}

export function hasRequiredConfig(config = getConfig()) {
  return Boolean(config.notionToken && config.databaseId)
}

function env(name: string, fallback = '') {
  return (process.env[name] || fallback).trim()
}

function normalizeDatabaseId(value: string) {
  return value.replace(/-/g, '')
}

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function positiveInt(value: string, fallback: number) {
  const parsed = Number.parseInt(value, 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function publishedType(value: string): AppConfig['fields']['publishedType'] {
  if (value === 'select' || value === 'status') {
    return value
  }
  return 'checkbox'
}
