import { definePlugin } from '@halo-dev/ui-shared'
import { IconImageAddLine } from '@halo-dev/components'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'ToolsRoot',
      route: {
        path: 'photo-story-linker',
        name: 'PhotoStoryLinker',
        component: () => import('./views/HomeView.vue'),
        meta: {
          title: 'Photo Stories',
          searchable: true,
          permissions: ['plugin:photo-story-linker:manage'],
          menu: {
            name: 'Photo Stories',
            icon: markRaw(IconImageAddLine),
            priority: 20,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
