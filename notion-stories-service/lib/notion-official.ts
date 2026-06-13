import { AppConfig, getConfig, hasRequiredConfig } from './config'

type NotionRichText = {
  plain_text?: string
}

type NotionFile = {
  type?: 'external' | 'file'
  external?: { url?: string }
  file?: { url?: string }
}

type NotionProperty = {
  type?: string
  title?: NotionRichText[]
  rich_text?: NotionRichText[]
  date?: { start?: string }
  multi_select?: Array<{ name?: string }>
  select?: { name?: string } | null
  status?: { name?: string } | null
  files?: NotionFile[]
  url?: string | null
  number?: number | null
  formula?: {
    type?: string
    string?: string | null
    number?: number | null
    boolean?: boolean | null
    date?: { start?: string } | null
  }
}

type NotionPage = {
  id: string
  url?: string
  cover?: NotionFile | null
  last_edited_time?: string
  properties?: Record<string, NotionProperty>
}

type DatabaseQueryResponse = {
  results?: NotionPage[]
  has_more?: boolean
  next_cursor?: string | null
}

export type StorySummary = {
  pageId: string
  title: string
  slug: string
  date: string
  summary: string
  tags: string[]
  coverUrl: string
  notionUrl: string
  lastEditedTime: string
}

export type StoriesResult =
  | { configMissing: true; stories: StorySummary[] }
  | { configMissing: false; stories: StorySummary[] }

export async function listStories(): Promise<StoriesResult> {
  const config = getConfig()
  if (!hasRequiredConfig(config)) {
    return { configMissing: true, stories: [] }
  }

  const stories: StorySummary[] = []
  let startCursor: string | undefined
  do {
    const response = await queryDatabase(config, buildListBody(config, startCursor))
    for (const page of response.results || []) {
      stories.push(parseStory(config, page))
    }
    startCursor = response.has_more && response.next_cursor ? response.next_cursor : undefined
  } while (startCursor)

  return {
    configMissing: false,
    stories: stories.filter((story) => story.slug)
  }
}

export async function getStoryBySlug(slug: string): Promise<StorySummary | null> {
  const config = getConfig()
  if (!hasRequiredConfig(config)) {
    return null
  }

  const response = await queryDatabase(config, buildDetailBody(config, slug))
  const page = response.results?.[0]
  return page ? parseStory(config, page) : null
}

function buildListBody(config: AppConfig, startCursor?: string) {
  return withCursor(config, {
    page_size: 100,
    filter: visibilityFilter(config),
    sorts: sortRules(config)
  }, startCursor)
}

function buildDetailBody(config: AppConfig, slug: string) {
  const filter = combineFilters([
    visibilityFilter(config),
    {
      property: config.fields.slug,
      rich_text: {
        equals: slug
      }
    }
  ])

  return {
    page_size: 1,
    filter
  }
}

async function queryDatabase(config: AppConfig, body: unknown): Promise<DatabaseQueryResponse> {
  const response = await fetch(`https://api.notion.com/v1/databases/${config.databaseId}/query`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${config.notionToken}`,
      'Content-Type': 'application/json',
      'Notion-Version': '2022-06-28'
    },
    body: JSON.stringify(body),
    next: {
      revalidate: config.cacheTtlSeconds
    }
  })

  if (!response.ok) {
    const detail = await response.text()
    throw new Error(`Notion database query failed: ${response.status} ${detail}`)
  }

  return response.json() as Promise<DatabaseQueryResponse>
}

function visibilityFilter(config: AppConfig) {
  const { published, publishedType, publishedValue } = config.fields
  if (!published) {
    return undefined
  }

  if (publishedType === 'checkbox') {
    return {
      property: published,
      checkbox: {
        equals: true
      }
    }
  }

  return {
    property: published,
    [publishedType]: {
      equals: publishedValue
    }
  }
}

function sortRules(config: AppConfig) {
  if (!config.fields.date) {
    return undefined
  }
  return [
    {
      property: config.fields.date,
      direction: 'descending'
    }
  ]
}

function combineFilters(filters: Array<Record<string, unknown> | undefined>) {
  const present = filters.filter(Boolean) as Array<Record<string, unknown>>
  if (present.length === 0) {
    return undefined
  }
  if (present.length === 1) {
    return present[0]
  }
  return {
    and: present
  }
}

function withCursor<T extends Record<string, unknown>>(config: AppConfig, body: T, startCursor?: string) {
  const filter = body.filter || visibilityFilter(config)
  const sorts = body.sorts || sortRules(config)
  return {
    ...body,
    ...(filter ? { filter } : {}),
    ...(sorts ? { sorts } : {}),
    ...(startCursor ? { start_cursor: startCursor } : {})
  }
}

function parseStory(config: AppConfig, page: NotionPage): StorySummary {
  const properties = page.properties || {}
  const coverFromProperty = propertyFileUrl(properties[config.fields.cover])
  const coverFromPage = fileUrl(page.cover || undefined)

  return {
    pageId: page.id,
    title: propertyText(properties[config.fields.title]) || '未命名',
    slug: propertyText(properties[config.fields.slug]) || page.id.replace(/-/g, ''),
    date: propertyDate(properties[config.fields.date]),
    summary: propertyText(properties[config.fields.summary]),
    tags: propertyTags(properties[config.fields.tags]),
    coverUrl: coverFromProperty || coverFromPage,
    notionUrl: page.url || '',
    lastEditedTime: page.last_edited_time || ''
  }
}

function propertyText(property?: NotionProperty) {
  if (!property) {
    return ''
  }

  switch (property.type) {
    case 'title':
      return richText(property.title)
    case 'rich_text':
      return richText(property.rich_text)
    case 'url':
      return property.url || ''
    case 'number':
      return property.number === null || property.number === undefined ? '' : String(property.number)
    case 'select':
      return property.select?.name || ''
    case 'status':
      return property.status?.name || ''
    case 'formula':
      return formulaText(property)
    default:
      return ''
  }
}

function propertyDate(property?: NotionProperty) {
  return property?.date?.start || ''
}

function propertyTags(property?: NotionProperty) {
  if (!property) {
    return []
  }
  if (property.type === 'multi_select') {
    return (property.multi_select || []).map((item) => item.name || '').filter(Boolean)
  }
  if (property.type === 'select' && property.select?.name) {
    return [property.select.name]
  }
  return []
}

function propertyFileUrl(property?: NotionProperty) {
  if (!property) {
    return ''
  }
  if (property.type === 'url') {
    return property.url || ''
  }
  if (property.type === 'files') {
    return fileUrl(property.files?.[0])
  }
  return ''
}

function fileUrl(file?: NotionFile | null) {
  if (!file) {
    return ''
  }
  if (file.type === 'external') {
    return file.external?.url || ''
  }
  if (file.type === 'file') {
    return file.file?.url || ''
  }
  return ''
}

function richText(nodes?: NotionRichText[]) {
  return (nodes || []).map((node) => node.plain_text || '').join('').trim()
}

function formulaText(property: NotionProperty) {
  const formula = property.formula
  if (!formula) {
    return ''
  }
  switch (formula.type) {
    case 'string':
      return formula.string || ''
    case 'number':
      return formula.number === null || formula.number === undefined ? '' : String(formula.number)
    case 'boolean':
      return formula.boolean === null || formula.boolean === undefined ? '' : String(formula.boolean)
    case 'date':
      return formula.date?.start || ''
    default:
      return ''
  }
}
