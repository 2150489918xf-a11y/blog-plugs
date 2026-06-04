# photo-story-linker

A Halo plugin that binds gallery photos to story posts and enhances the public photo gallery with story previews.

## Features

- Bind one photo to one Halo post.
- Bind multiple photos to the same story post.
- Show public story links on the gallery page.
- Keep story content in Halo's native post system.
- Preserve photos and posts when the plugin is disabled.

## Requirements

- Halo 2.24+
- Java 21+
- Node.js 18+
- pnpm

## Development

```bash
./gradlew haloServer
```

For console UI development:

```bash
cd ui
pnpm install
pnpm dev
```

## Build

```bash
./gradlew build
```

The plugin jar is generated in `build/libs`.

## License

[GPL-3.0](./LICENSE)
