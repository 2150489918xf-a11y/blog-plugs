<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'

type Metadata = {
  name: string
  creationTimestamp?: string
  deletionTimestamp?: string
}

type Photo = {
  metadata: Metadata
  spec: {
    displayName?: string
    description?: string
    url?: string
    cover?: string
    groupName?: string
    tags?: string[]
  }
}

type Post = {
  metadata: Metadata
  spec: {
    title?: string
    publish?: boolean
    deleted?: boolean
    visible?: 'PUBLIC' | 'INTERNAL' | 'PRIVATE'
    slug?: string
  }
  status?: {
    permalink?: string
    excerpt?: string
  }
}

type Binding = {
  metadata: Metadata
  spec: {
    photoName: string
    postName: string
    teaser?: string
    badgeText?: string
    enabled?: boolean
    openMode?: 'SAME_TAB' | 'NEW_TAB'
  }
}

type Mode = 'existing' | 'new'

const bindingApi = '/apis/photo-story-linker.xiongfan.me/v1alpha1/photostorybindings'
const photoApi = '/apis/core.halo.run/v1alpha1/photos'
const postApi = '/apis/content.halo.run/v1alpha1/posts'
const consolePostApi = '/apis/api.console.halo.run/v1alpha1/posts'

const bindings = ref<Binding[]>([])
const photos = ref<Photo[]>([])
const posts = ref<Post[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const query = ref('')
const postQuery = ref('')
const selectedPhotoName = ref('')
const mode = ref<Mode>('existing')

const form = ref({
  postName: '',
  storyTitle: '',
  teaser: '',
  badgeText: '阅读故事',
  openMode: 'SAME_TAB' as 'SAME_TAB' | 'NEW_TAB',
})

const bindingByPhotoName = computed(() => {
  return new Map(bindings.value.map((binding) => [binding.spec.photoName, binding]))
})

const photoByName = computed(() => new Map(photos.value.map((photo) => [photo.metadata.name, photo])))
const postByName = computed(() => new Map(posts.value.map((post) => [post.metadata.name, post])))

const selectedPhoto = computed(() => photoByName.value.get(selectedPhotoName.value))
const selectedBinding = computed(() => bindingByPhotoName.value.get(selectedPhotoName.value))
const selectedPost = computed(() => {
  const postName = selectedBinding.value?.spec.postName || form.value.postName
  return postByName.value.get(postName)
})

const availablePosts = computed(() =>
  posts.value.filter((post) => !post.spec.deleted && !post.metadata.deletionTimestamp)
)

const filteredPhotos = computed(() => {
  const keyword = normalize(query.value).toLowerCase()
  if (!keyword) {
    return photos.value
  }
  return photos.value.filter((photo) => {
    const text = [
      photo.metadata.name,
      photo.spec.displayName,
      photo.spec.description,
      photo.spec.groupName,
      ...(photo.spec.tags || []),
    ]
      .join(' ')
      .toLowerCase()
    return text.includes(keyword)
  })
})

const filteredPosts = computed(() => {
  const keyword = normalize(postQuery.value).toLowerCase()
  if (!keyword) {
    return availablePosts.value
  }
  return availablePosts.value.filter((post) => {
    const text = [post.metadata.name, post.spec.title, post.status?.excerpt, post.status?.permalink]
      .join(' ')
      .toLowerCase()
    return text.includes(keyword)
  })
})

const stats = computed(() => {
  const enabled = bindings.value.filter((binding) => binding.spec.enabled !== false).length
  return {
    photos: photos.value.length,
    bindings: bindings.value.length,
    enabled,
  }
})

function normalize(value?: string) {
  return String(value || '').trim()
}

function slugify(value: string) {
  const cleaned = normalize(value)
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return cleaned || `story-${Date.now()}`
}

function selectPhoto(photo: Photo) {
  selectedPhotoName.value = photo.metadata.name
  const binding = bindingByPhotoName.value.get(photo.metadata.name)
  if (binding) {
    mode.value = 'existing'
    form.value.postName = binding.spec.postName
    form.value.teaser = binding.spec.teaser || ''
    form.value.badgeText = binding.spec.badgeText || '阅读故事'
    form.value.openMode = binding.spec.openMode || 'SAME_TAB'
    return
  }
  form.value.postName = availablePosts.value[0]?.metadata.name || ''
  form.value.storyTitle = photo.spec.displayName ? `${photo.spec.displayName}的故事` : ''
  form.value.teaser = photo.spec.description || ''
  form.value.badgeText = '阅读故事'
  form.value.openMode = 'SAME_TAB'
}

function photoTitle(name: string) {
  const photo = photoByName.value.get(name)
  return photo?.spec.displayName || name
}

function postTitle(name: string) {
  const post = postByName.value.get(name)
  return post?.spec.title || name
}

function postStatus(post?: Post) {
  if (!post) {
    return '文章不存在'
  }
  if (post.spec.deleted || post.metadata.deletionTimestamp) {
    return '已删除'
  }
  if (!post.spec.publish) {
    return '草稿'
  }
  if (post.spec.visible !== 'PUBLIC') {
    return '非公开'
  }
  return '公开'
}

function postEditUrl(postName?: string) {
  if (!postName) {
    return ''
  }
  return `/console/posts/editor?name=${encodeURIComponent(postName)}`
}

async function fetchAll() {
  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    const [bindingRes, photoRes, postRes] = await Promise.all([
      axios.get(bindingApi, { params: { page: 1, size: 500 } }),
      axios.get(photoApi, { params: { page: 1, size: 500 } }),
      axios.get(postApi, { params: { page: 1, size: 500 } }),
    ])
    bindings.value = bindingRes.data.items || []
    photos.value = photoRes.data.items || []
    posts.value = postRes.data.items || []
    if (!selectedPhotoName.value && photos.value.length) {
      selectPhoto(photos.value[0])
    } else if (selectedPhotoName.value) {
      const photo = photoByName.value.get(selectedPhotoName.value)
      if (photo) {
        selectPhoto(photo)
      }
    }
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    loading.value = false
  }
}

async function upsertBinding(postName: string) {
  if (!selectedPhotoName.value || !postName) {
    error.value = '请选择图片和故事文章。'
    return
  }
  const current = selectedBinding.value
  const payload = {
    apiVersion: 'photo-story-linker.xiongfan.me/v1alpha1',
    kind: 'PhotoStoryBinding',
    metadata: {
      name: current?.metadata.name || `photo-story-${Date.now()}`,
    },
    spec: {
      photoName: selectedPhotoName.value,
      postName,
      teaser: form.value.teaser,
      badgeText: form.value.badgeText || '阅读故事',
      enabled: true,
      openMode: form.value.openMode,
    },
  }
  if (current) {
    await axios.patch(
      `${bindingApi}/${current.metadata.name}`,
      [
        { op: 'add', path: '/spec/photoName', value: payload.spec.photoName },
        { op: 'add', path: '/spec/postName', value: payload.spec.postName },
        { op: 'add', path: '/spec/teaser', value: payload.spec.teaser },
        { op: 'add', path: '/spec/badgeText', value: payload.spec.badgeText },
        { op: 'add', path: '/spec/enabled', value: payload.spec.enabled },
        { op: 'add', path: '/spec/openMode', value: payload.spec.openMode },
      ],
      {
        headers: { 'Content-Type': 'application/json-patch+json' },
      }
    )
  } else {
    await axios.post(bindingApi, payload)
  }
}

async function bindExistingPost() {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await upsertBinding(form.value.postName)
    notice.value = '已保存图片故事绑定。'
    await fetchAll()
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    saving.value = false
  }
}

async function createStoryDraft() {
  if (!selectedPhoto.value) {
    error.value = '请先选择一张图片。'
    return
  }
  const title = normalize(form.value.storyTitle) || `${selectedPhoto.value.spec.displayName || '图片'}的故事`
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const content = [
      selectedPhoto.value.spec.url ? `![${selectedPhoto.value.spec.displayName || title}](${selectedPhoto.value.spec.url})` : '',
      '',
      form.value.teaser || selectedPhoto.value.spec.description || '',
    ]
      .filter(Boolean)
      .join('\n')
    const { data: post } = await axios.post(consolePostApi, {
      post: {
        apiVersion: 'content.halo.run/v1alpha1',
        kind: 'Post',
        metadata: {
          name: '',
          generateName: 'post-',
          annotations: {
            'photo-story-linker.xiongfan.me/photo-name': selectedPhoto.value.metadata.name,
          },
        },
        spec: {
          title,
          slug: slugify(title),
          publish: false,
          deleted: false,
          pinned: false,
          allowComment: true,
          priority: 0,
          visible: 'PUBLIC',
          excerpt: {
            autoGenerate: !normalize(form.value.teaser),
            raw: normalize(form.value.teaser) || undefined,
          },
          categories: [],
          tags: ['图片故事'],
        },
      },
      content: {
        rawType: 'markdown',
        raw: content,
        content,
      },
    })
    form.value.postName = post.metadata.name
    await upsertBinding(post.metadata.name)
    notice.value = '已创建故事草稿并完成绑定。发布文章后，前台访客即可看到故事入口。'
    await fetchAll()
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    saving.value = false
  }
}

async function toggleBinding(binding: Binding) {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await axios.patch(
      `${bindingApi}/${binding.metadata.name}`,
      [
        {
          op: 'add',
          path: '/spec/enabled',
          value: binding.spec.enabled === false,
        },
      ],
      {
        headers: { 'Content-Type': 'application/json-patch+json' },
      }
    )
    await fetchAll()
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    saving.value = false
  }
}

async function deleteBinding(binding: Binding) {
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    await axios.delete(`${bindingApi}/${binding.metadata.name}`)
    notice.value = '已解除绑定，图片和文章都不会被删除。'
    await fetchAll()
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    saving.value = false
  }
}

onMounted(fetchAll)
</script>

<template>
  <main class="psl-page">
    <header class="psl-header">
      <div>
        <h1>Photo Stories</h1>
        <p>把图库图片绑定到 Halo 文章；故事内容继续由 Halo 原生文章系统管理。</p>
      </div>
      <button type="button" class="psl-button psl-button-secondary" :disabled="loading" @click="fetchAll">
        {{ loading ? '加载中...' : '刷新' }}
      </button>
    </header>

    <section class="psl-stats">
      <div>
        <strong>{{ stats.photos }}</strong>
        <span>图片</span>
      </div>
      <div>
        <strong>{{ stats.bindings }}</strong>
        <span>绑定</span>
      </div>
      <div>
        <strong>{{ stats.enabled }}</strong>
        <span>启用中</span>
      </div>
    </section>

    <p v-if="error" class="psl-message psl-error">{{ error }}</p>
    <p v-if="notice" class="psl-message psl-notice">{{ notice }}</p>

    <section class="psl-workspace">
      <aside class="psl-panel psl-photo-panel">
        <div class="psl-panel-head">
          <h2>选择图片</h2>
          <input v-model="query" type="search" placeholder="搜索图片、描述、分组或标签" />
        </div>
        <div v-if="!photos.length && !loading" class="psl-empty">未检测到图库图片，请先启用图库管理并添加图片。</div>
        <div v-else class="psl-photo-list">
          <button
            v-for="photo in filteredPhotos"
            :key="photo.metadata.name"
            type="button"
            class="psl-photo-item"
            :class="{ 'is-active': selectedPhotoName === photo.metadata.name }"
            @click="selectPhoto(photo)"
          >
            <img :src="photo.spec.cover || photo.spec.url" :alt="photo.spec.displayName || photo.metadata.name" />
            <span>
              <strong>{{ photo.spec.displayName || photo.metadata.name }}</strong>
              <small>{{ bindingByPhotoName.has(photo.metadata.name) ? '已绑定故事' : '未绑定' }}</small>
            </span>
          </button>
        </div>
      </aside>

      <section class="psl-panel psl-editor-panel">
        <div class="psl-panel-head">
          <h2>{{ selectedPhoto ? '绑定故事' : '等待选择图片' }}</h2>
          <span v-if="selectedBinding" class="psl-pill">当前已绑定</span>
        </div>

        <div v-if="selectedPhoto" class="psl-editor">
          <div class="psl-selected-photo">
            <img :src="selectedPhoto.spec.cover || selectedPhoto.spec.url" :alt="selectedPhoto.spec.displayName" />
            <div>
              <strong>{{ selectedPhoto.spec.displayName || selectedPhoto.metadata.name }}</strong>
              <p>{{ selectedPhoto.spec.description || '这张图片暂无图库描述。' }}</p>
            </div>
          </div>

          <div class="psl-tabs" role="tablist" aria-label="Story mode">
            <button type="button" :class="{ 'is-active': mode === 'existing' }" @click="mode = 'existing'">
              绑定已有文章
            </button>
            <button type="button" :class="{ 'is-active': mode === 'new' }" @click="mode = 'new'">
              创建故事草稿
            </button>
          </div>

          <form v-if="mode === 'existing'" class="psl-form" @submit.prevent="bindExistingPost">
            <label>
              <span>搜索文章</span>
              <input v-model="postQuery" type="search" placeholder="输入标题或链接过滤文章" />
            </label>
            <label>
              <span>故事文章</span>
              <select v-model="form.postName">
                <option v-for="post in filteredPosts" :key="post.metadata.name" :value="post.metadata.name">
                  {{ post.spec.title || post.metadata.name }} - {{ postStatus(post) }}
                </option>
              </select>
            </label>
            <label>
              <span>悬停预览文案</span>
              <textarea v-model="form.teaser" rows="4" placeholder="留空时使用文章摘要。"></textarea>
            </label>
            <div class="psl-two">
              <label>
                <span>按钮文案</span>
                <input v-model="form.badgeText" type="text" />
              </label>
              <label>
                <span>打开方式</span>
                <select v-model="form.openMode">
                  <option value="SAME_TAB">当前窗口</option>
                  <option value="NEW_TAB">新窗口</option>
                </select>
              </label>
            </div>
            <div class="psl-actions">
              <button type="submit" class="psl-button" :disabled="saving || !form.postName">
                {{ selectedBinding ? '保存绑定' : '绑定文章' }}
              </button>
              <a v-if="selectedPost" class="psl-link-button" :href="postEditUrl(selectedPost.metadata.name)">
                编辑文章
              </a>
            </div>
          </form>

          <form v-else class="psl-form" @submit.prevent="createStoryDraft">
            <label>
              <span>故事标题</span>
              <input v-model="form.storyTitle" type="text" placeholder="例如：一次黄昏里的火箭发射" />
            </label>
            <label>
              <span>草稿摘要</span>
              <textarea v-model="form.teaser" rows="4" placeholder="会作为故事预览文案，也会写入文章摘要。"></textarea>
            </label>
            <div class="psl-two">
              <label>
                <span>按钮文案</span>
                <input v-model="form.badgeText" type="text" />
              </label>
              <label>
                <span>打开方式</span>
                <select v-model="form.openMode">
                  <option value="SAME_TAB">当前窗口</option>
                  <option value="NEW_TAB">新窗口</option>
                </select>
              </label>
            </div>
            <div class="psl-actions">
              <button type="submit" class="psl-button" :disabled="saving">
                创建草稿并绑定
              </button>
            </div>
          </form>
        </div>

        <div v-else class="psl-empty">请先从左侧选择一张图片。</div>
      </section>

      <aside class="psl-panel psl-bindings-panel">
        <div class="psl-panel-head">
          <h2>当前绑定</h2>
        </div>
        <div v-if="!bindings.length" class="psl-empty">还没有图片故事绑定。</div>
        <article v-for="binding in bindings" :key="binding.metadata.name" class="psl-binding">
          <div>
            <strong>{{ photoTitle(binding.spec.photoName) }}</strong>
            <span>{{ postTitle(binding.spec.postName) }}</span>
            <small>{{ binding.spec.enabled === false ? '已停用' : postStatus(postByName.get(binding.spec.postName)) }}</small>
          </div>
          <div class="psl-binding-actions">
            <button type="button" class="psl-mini" :disabled="saving" @click="toggleBinding(binding)">
              {{ binding.spec.enabled === false ? '启用' : '停用' }}
            </button>
            <button type="button" class="psl-mini psl-danger" :disabled="saving" @click="deleteBinding(binding)">
              解除
            </button>
          </div>
        </article>
      </aside>
    </section>
  </main>
</template>

<style scoped>
.psl-page {
  width: 100%;
  min-height: 100%;
  padding: 1rem;
  color: #111827;
  background: #f8fafc;
}

.psl-header,
.psl-panel-head,
.psl-actions,
.psl-binding-actions {
  display: flex;
  gap: 0.75rem;
}

.psl-header {
  flex-direction: column;
  margin-bottom: 1rem;
}

.psl-header h1,
.psl-panel h2 {
  margin: 0;
  line-height: 1.25;
}

.psl-header p {
  max-width: 64ch;
  margin: 0.35rem 0 0;
  color: #4b5563;
}

.psl-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.psl-stats div,
.psl-panel {
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  background: #fff;
}

.psl-stats div {
  display: grid;
  gap: 0.15rem;
  padding: 0.875rem;
}

.psl-stats strong {
  font-size: 1.35rem;
}

.psl-stats span,
.psl-empty,
.psl-binding span,
.psl-binding small,
.psl-selected-photo p {
  color: #6b7280;
}

.psl-message {
  padding: 0.75rem 1rem;
  border-radius: 0.375rem;
}

.psl-error {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
}

.psl-notice {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #166534;
}

.psl-workspace {
  display: grid;
  gap: 1rem;
}

.psl-panel {
  display: grid;
  gap: 1rem;
  align-content: start;
  padding: 1rem;
}

.psl-panel-head {
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.psl-panel-head input,
.psl-form input,
.psl-form select,
.psl-form textarea {
  width: 100%;
  min-height: 2.75rem;
  padding: 0.625rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  background: #fff;
  font: inherit;
}

.psl-photo-list {
  display: grid;
  gap: 0.625rem;
  max-height: 36rem;
  overflow: auto;
}

.psl-photo-item {
  width: 100%;
  min-height: 4.5rem;
  display: grid;
  grid-template-columns: 4.5rem 1fr;
  gap: 0.75rem;
  align-items: center;
  padding: 0.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  background: #fff;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.psl-photo-item.is-active {
  border-color: #111827;
  box-shadow: 0 0 0 2px rgba(17, 24, 39, 0.08);
}

.psl-photo-item img,
.psl-selected-photo img {
  width: 100%;
  height: 100%;
  border-radius: 0.375rem;
  object-fit: cover;
  background: #e5e7eb;
}

.psl-photo-item span,
.psl-binding div {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.psl-photo-item strong,
.psl-binding strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.psl-editor,
.psl-form {
  display: grid;
  gap: 1rem;
}

.psl-selected-photo {
  display: grid;
  grid-template-columns: 6rem 1fr;
  gap: 0.875rem;
  align-items: center;
}

.psl-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
  padding: 0.25rem;
  border-radius: 0.5rem;
  background: #f3f4f6;
}

.psl-tabs button,
.psl-mini,
.psl-button,
.psl-link-button {
  min-height: 2.75rem;
  border: 0;
  border-radius: 0.375rem;
  font-weight: 700;
  cursor: pointer;
}

.psl-tabs button {
  background: transparent;
  color: #4b5563;
}

.psl-tabs button.is-active {
  background: #fff;
  color: #111827;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.psl-form label {
  display: grid;
  gap: 0.375rem;
  font-size: 0.875rem;
  font-weight: 700;
}

.psl-two {
  display: grid;
  gap: 1rem;
}

.psl-actions {
  flex-wrap: wrap;
  align-items: center;
}

.psl-button,
.psl-link-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.625rem 0.875rem;
  background: #111827;
  color: #fff;
  text-decoration: none;
}

.psl-button-secondary {
  background: #e5e7eb;
  color: #111827;
}

.psl-button:disabled,
.psl-mini:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.psl-pill {
  display: inline-flex;
  align-items: center;
  min-height: 1.75rem;
  padding: 0 0.5rem;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 0.75rem;
  font-weight: 700;
}

.psl-binding {
  display: grid;
  gap: 0.75rem;
  padding: 0.875rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
}

.psl-binding-actions {
  flex-wrap: wrap;
}

.psl-mini {
  padding: 0 0.75rem;
  background: #f3f4f6;
  color: #111827;
}

.psl-danger {
  background: #fef2f2;
  color: #991b1b;
}

@media (min-width: 768px) {
  .psl-page {
    padding: 1.5rem;
  }

  .psl-header {
    flex-direction: row;
    align-items: start;
    justify-content: space-between;
  }

  .psl-two {
    grid-template-columns: 1fr 1fr;
  }
}

@media (min-width: 1280px) {
  .psl-workspace {
    grid-template-columns: minmax(18rem, 23rem) minmax(28rem, 1fr) minmax(18rem, 24rem);
    align-items: start;
  }
}
</style>
