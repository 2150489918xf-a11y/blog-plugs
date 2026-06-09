import { definePlugin } from '@halo-dev/ui-shared'
import { IconImageAddLine } from '@halo-dev/components'
import { markRaw } from 'vue'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/photo-story-linker',
        name: 'PhotoStoryLinker',
        component: () => import('./views/HomeView.vue'),
        meta: {
          title: '日记故事',
          searchable: true,
          permissions: ['plugin:photo-story-linker:manage'],
          menu: {
            name: '日记故事',
            group: 'content',
            icon: markRaw(IconImageAddLine),
            priority: 20,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
