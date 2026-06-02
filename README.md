# Halo CFBed Storage Plugin

Halo attachment storage plugin for the CloudFlare ImgBed upload API.

## Features

- Upload Halo attachments to a CFBed-compatible image bed.
- Configure image bed URL, API token, upload channel, folder, and public URL in the Halo plugin settings page.
- Automatically create and sync the Halo storage policy.
- Optionally set the synced policy as the default upload policy for the Halo console/editor.

## Requirements

- Halo `>= 2.22.0`
- Java 21 for building

## Build

```bash
./gradlew build
```

The plugin jar will be generated in `build/libs/`.

## Configuration

After installing and enabling the plugin in Halo Console, open the plugin details page and configure the **CFBed 图床** tab.

Required fields:

- 图床地址: for example `https://imgbed.example.com`
- API Token: your CFBed API token
- 上传渠道: for example `cfr2`
- 上传目录: for example `halo`

Do not include `/api` in the image bed address. The plugin sends uploads to `/upload`.
