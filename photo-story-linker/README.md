# photo-story-linker

A Halo plugin that binds gallery photos to story posts and enhances the public photo gallery with story previews.

## Features

- Bind one photo to one Halo post.
- Bind multiple photos to the same story post.
- Create a Halo post draft from a selected photo and bind it automatically.
- Manage bindings from a dedicated Console workspace.
- Enable, disable, or remove bindings without deleting photos or posts.
- Show public story links on the gallery page.
- Merge the story preview with the gallery hover layer instead of stacking duplicate overlays.
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
