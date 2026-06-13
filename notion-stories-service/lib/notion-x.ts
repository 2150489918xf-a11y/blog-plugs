import { NotionAPI } from 'notion-client'

export async function getNotionRecordMap(pageId: string) {
  const notion = new NotionAPI({
    authToken: process.env.NOTION_X_AUTH_TOKEN || undefined,
    activeUser: process.env.NOTION_X_ACTIVE_USER || undefined
  })

  return notion.getPage(pageId)
}
