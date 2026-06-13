'use client'

import dynamic from 'next/dynamic'
import type { ExtendedRecordMap } from 'notion-types'
import { NotionRenderer } from 'react-notion-x'

const Code = dynamic(() =>
  import('react-notion-x/build/third-party/code').then((module) => module.Code)
)
const Collection = dynamic(() =>
  import('react-notion-x/build/third-party/collection').then((module) => module.Collection)
)
const Equation = dynamic(() =>
  import('react-notion-x/build/third-party/equation').then((module) => module.Equation)
)
const Modal = dynamic(() =>
  import('react-notion-x/build/third-party/modal').then((module) => module.Modal),
  { ssr: false }
)

type NotionPageProps = {
  pageId: string
  recordMap: ExtendedRecordMap
}

export function NotionPage({ pageId, recordMap }: NotionPageProps) {
  return (
    <NotionRenderer
      recordMap={recordMap}
      rootPageId={pageId}
      fullPage={false}
      darkMode={false}
      previewImages
      showCollectionViewDropdown={false}
      components={{
        Code,
        Collection,
        Equation,
        Modal
      }}
    />
  )
}
