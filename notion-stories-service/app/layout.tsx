import type { Metadata } from 'next'
import 'react-notion-x/src/styles.css'
import 'prismjs/themes/prism-tomorrow.css'
import 'katex/dist/katex.min.css'
import './globals.css'
import { getConfig } from '../lib/config'

const config = getConfig()

export const metadata: Metadata = {
  metadataBase: new URL(config.siteUrl),
  title: {
    default: config.siteTitle,
    template: `%s | ${config.siteTitle}`
  },
  description: config.siteDescription
}

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  )
}
