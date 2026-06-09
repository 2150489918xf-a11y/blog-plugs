<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'

type Metadata = {
  name: string
  creationTimestamp?: string
  deletionTimestamp?: string
}

type Visible = 'PUBLIC' | 'INTERNAL' | 'PRIVATE'
type TargetKind = 'POST' | 'SINGLE_PAGE'
type Mode = 'journal' | 'existing'

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

type ContentItem = {
  metadata: Metadata
  spec: {
    title?: string
    publish?: boolean
    deleted?: boolean
    visible?: Visible
    slug?: string
    cover?: string
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
    postName?: string
    targetKind?: TargetKind
    targetName?: string
    teaser?: string
    badgeText?: string
    enabled?: boolean
    openMode?: 'SAME_TAB' | 'NEW_TAB'
  }
}

type JournalEntry = {
  metadata: Metadata
  spec: {
    singlePageName: string
    coverPhotoName?: string
    photoNames?: string[]
    journalDate?: string
    teaser?: string
    enabled?: boolean
    showInJournalList?: boolean
    mood?: string
    location?: string
    weather?: string
  }
}

const bindingApi = '/apis/photo-story-linker.xiongfan.me/v1alpha1/photostorybindings'
const journalApi = '/apis/photo-story-linker.xiongfan.me/v1alpha1/journalentries'
const photoApi = '/apis/core.halo.run/v1alpha1/photos'
const postApi = '/apis/content.halo.run/v1alpha1/posts'
const singlePageApi = '/apis/content.halo.run/v1alpha1/singlepages'
const consoleSinglePageApi = '/apis/api.console.halo.run/v1alpha1/singlepages'

const bindings = ref<Binding[]>([])
const journals = ref<JournalEntry[]>([])
const photos = ref<Photo[]>([])
const posts = ref<ContentItem[]>([])
const pages = ref<ContentItem[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const query = ref('')
const targetQuery = ref('')
const selectedPhotoName = ref('')
const mode = ref<Mode>('journal')

const form = ref({
  targetKind: 'SINGLE_PAGE' as TargetKind,
  targetName: '',
  title: '',
  teaser: '',
  badgeText: '阅读日记',
  openMode: 'SAME_TAB' as 'SAME_TAB' | 'NEW_TAB',
  visible: 'PUBLIC' as Visible,
  showInJournalList: true,
  journalDate: today(),
  mood: '',
  location: '',
  weather: '',
})

const bindingByPhotoName = computed(() => {
  return new Map(bindings.value.map((binding) => [binding.spec.photoName, binding]))
})

const photoByName = computed(() => new Map(photos.value.map((photo) => [photo.metadata.name, photo])))
const postByName = computed(() => new Map(posts.value.map((post) => [post.metadata.name, post])))
const pageByName = computed(() => new Map(pages.value.map((page) => [page.metadata.name, page])))
const journalByPageName = computed(() => {
  return new Map(journals.value.map((journal) => [journal.spec.singlePageName, journal]))
})

const selectedPhoto = computed(() => photoByName.value.get(selectedPhotoName.value))
const selectedBinding = computed(() => bindingByPhotoName.value.get(selectedPhotoName.value))

const selectedTarget = computed(() => {
  if (form.value.targetKind === 'POST') {
    return postByName.value.get(form.value.targetName)
  }
  return pageByName.value.get(form.value.targetName)
})

const availablePosts = computed(() =>
  posts.value.filter((post) => !post.spec.deleted && !post.metadata.deletionTimestamp)
)

const availablePages = computed(() =>
  pages.value.filter((page) => !page.spec.deleted && !page.metadata.deletionTimestamp)
)

const filteredPhotos = computed(() => {
  const keyword = normalize(query.value).toLowerCase()
  if (!keyword) {
    return photos.value
  }
  return photos.value.filter((photo) =>
    [
      photo.metadata.name,
      photo.spec.displayName,
      photo.spec.description,
      photo.spec.groupName,
      ...(photo.spec.tags || []),
    ]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  )
})

const filteredTargets = computed(() => {
  const source = form.value.targetKind === 'POST' ? availablePosts.value : availablePages.value
  const keyword = normalize(targetQuery.value).toLowerCase()
  if (!keyword) {
    return source
  }
  return source.filter((item) =>
    [item.metadata.name, item.spec.title, item.status?.excerpt, item.status?.permalink]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  )
})

const stats = computed(() => ({
  journals: journals.value.length,
  bindings: bindings.value.length,
  photos: photos.value.length,
}))

const saveExistingDisabled = computed(() => {
  if (saving.value || !form.value.targetName) {
    return true
  }
  return form.value.targetKind === 'POST' && !selectedPhotoName.value
})

function normalize(value?: string) {
  return String(value || '').trim()
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

function slugify(value: string) {
  const cleaned = normalize(value)
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return cleaned || `journal-${Date.now()}`
}

function journalNameFor(singlePageName: string) {
  return `journal-${singlePageName}`.replace(/[^a-z0-9-]/gi, '-').toLowerCase()
}

function resolveBindingTarget(binding: Binding) {
  const targetKind = binding.spec.targetKind || 'POST'
  const targetName = binding.spec.targetName || binding.spec.postName || ''
  return { targetKind, targetName }
}

function applyJournalToForm(singlePageName: string) {
  const journal = journalByPageName.value.get(singlePageName)
  if (!journal) {
    return
  }
  form.value.journalDate = journal.spec.journalDate || today()
  form.value.teaser = journal.spec.teaser || form.value.teaser
  form.value.showInJournalList = journal.spec.showInJournalList !== false
  form.value.mood = journal.spec.mood || ''
  form.value.location = journal.spec.location || ''
  form.value.weather = journal.spec.weather || ''
}

function resetJournalFields() {
  form.value.visible = 'PUBLIC'
  form.value.showInJournalList = true
  form.value.journalDate = today()
  form.value.mood = ''
  form.value.location = ''
  form.value.weather = ''
}

function selectNoPhoto() {
  selectedPhotoName.value = ''
  mode.value = 'journal'
  resetJournalFields()
  form.value.title = '新的日记'
  form.value.teaser = ''
  form.value.badgeText = '阅读日记'
  form.value.openMode = 'SAME_TAB'
  form.value.targetKind = 'SINGLE_PAGE'
  form.value.targetName = availablePages.value[0]?.metadata.name || ''
}

function selectPhoto(photo: Photo) {
  selectedPhotoName.value = photo.metadata.name
  const binding = bindingByPhotoName.value.get(photo.metadata.name)
  if (binding) {
    const target = resolveBindingTarget(binding)
    mode.value = 'existing'
    resetJournalFields()
    form.value.targetKind = target.targetKind
    form.value.targetName = target.targetName
    form.value.teaser = binding.spec.teaser || ''
    form.value.badgeText = binding.spec.badgeText || '阅读日记'
    form.value.openMode = binding.spec.openMode || 'SAME_TAB'
    if (target.targetKind === 'SINGLE_PAGE') {
      applyJournalToForm(target.targetName)
    }
    return
  }
  mode.value = 'journal'
  resetJournalFields()
  form.value.targetKind = 'SINGLE_PAGE'
  form.value.targetName = availablePages.value[0]?.metadata.name || ''
  form.value.title = photo.spec.displayName ? `${photo.spec.displayName}的日记` : '新的日记'
  form.value.teaser = photo.spec.description || ''
  form.value.badgeText = '阅读日记'
  form.value.openMode = 'SAME_TAB'
}

function photoTitle(name?: string) {
  if (!name) {
    return '未绑定图片'
  }
  const photo = photoByName.value.get(name)
  return photo?.spec.displayName || name
}

function targetTitle(kind?: TargetKind, name?: string) {
  if (!name) {
    return '未绑定内容'
  }
  const item = kind === 'SINGLE_PAGE' ? pageByName.value.get(name) : postByName.value.get(name)
  return item?.spec.title || name
}

function contentStatus(item?: ContentItem) {
  if (!item) {
    return '内容不存在'
  }
  if (item.spec.deleted || item.metadata.deletionTimestamp) {
    return '已删除'
  }
  if (!item.spec.publish) {
    return '草稿'
  }
  if (item.spec.visible === 'INTERNAL') {
    return '登录可见'
  }
  if (item.spec.visible === 'PRIVATE') {
    return '私密'
  }
  return '公开'
}

function editUrl(kind?: TargetKind, name?: string) {
  if (!name) {
    return ''
  }
  if (kind === 'POST') {
    return `/console/posts/editor?name=${encodeURIComponent(name)}`
  }
  return `/console/single-pages/editor?name=${encodeURIComponent(name)}`
}

function targetOptionsChanged() {
  form.value.targetName = filteredTargets.value[0]?.metadata.name || ''
}

async function fetchAll() {
  loading.value = true
  error.value = ''
  try {
    const [bindingRes, journalRes, photoRes, postRes, pageRes] = await Promise.all([
      axios.get(bindingApi, { params: { page: 1, size: 500 } }),
      axios.get(journalApi, { params: { page: 1, size: 500 } }),
      axios.get(photoApi, { params: { page: 1, size: 500 } }),
      axios.get(postApi, { params: { page: 1, size: 500 } }),
      axios.get(singlePageApi, { params: { page: 1, size: 500 } }),
    ])
    bindings.value = bindingRes.data.items || []
    journals.value = journalRes.data.items || []
    photos.value = photoRes.data.items || []
    posts.value = postRes.data.items || []
    pages.value = pageRes.data.items || []

    if (selectedPhotoName.value) {
      const photo = photoByName.value.get(selectedPhotoName.value)
      if (photo) {
        selectPhoto(photo)
      }
    } else if (!form.value.title) {
      selectNoPhoto()
    }
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    loading.value = false
  }
}

async function upsertBinding(targetKind: TargetKind, targetName: string) {
  if (!selectedPhotoName.value || !targetName) {
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
      postName: targetKind === 'POST' ? targetName : '',
      targetKind,
      targetName,
      teaser: form.value.teaser,
      badgeText: form.value.badgeText || '阅读日记',
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
        { op: 'add', path: '/spec/targetKind', value: payload.spec.targetKind },
        { op: 'add', path: '/spec/targetName', value: payload.spec.targetName },
        { op: 'add', path: '/spec/teaser', value: payload.spec.teaser },
        { op: 'add', path: '/spec/badgeText', value: payload.spec.badgeText },
        { op: 'add', path: '/spec/enabled', value: payload.spec.enabled },
        { op: 'add', path: '/spec/openMode', value: payload.spec.openMode },
      ],
      { headers: { 'Content-Type': 'application/json-patch+json' } }
    )
  } else {
    await axios.post(bindingApi, payload)
  }
}

async function upsertJournalEntry(singlePageName: string) {
  const current = journalByPageName.value.get(singlePageName)
  const photoNames = new Set(current?.spec.photoNames || [])
  if (selectedPhotoName.value) {
    photoNames.add(selectedPhotoName.value)
  }
  const payload = {
    apiVersion: 'photo-story-linker.xiongfan.me/v1alpha1',
    kind: 'JournalEntry',
    metadata: {
      name: current?.metadata.name || journalNameFor(singlePageName),
    },
    spec: {
      singlePageName,
      coverPhotoName: selectedPhotoName.value || current?.spec.coverPhotoName || '',
      photoNames: Array.from(photoNames),
      journalDate: form.value.journalDate || today(),
      teaser: form.value.teaser,
      enabled: true,
      showInJournalList: form.value.showInJournalList,
      mood: form.value.mood,
      location: form.value.location,
      weather: form.value.weather,
    },
  }
  if (current) {
    await axios.patch(
      `${journalApi}/${current.metadata.name}`,
      [
        { op: 'add', path: '/spec/singlePageName', value: payload.spec.singlePageName },
        { op: 'add', path: '/spec/coverPhotoName', value: payload.spec.coverPhotoName },
        { op: 'add', path: '/spec/photoNames', value: payload.spec.photoNames },
        { op: 'add', path: '/spec/journalDate', value: payload.spec.journalDate },
        { op: 'add', path: '/spec/teaser', value: payload.spec.teaser },
        { op: 'add', path: '/spec/enabled', value: payload.spec.enabled },
        { op: 'add', path: '/spec/showInJournalList', value: payload.spec.showInJournalList },
        { op: 'add', path: '/spec/mood', value: payload.spec.mood },
        { op: 'add', path: '/spec/location', value: payload.spec.location },
        { op: 'add', path: '/spec/weather', value: payload.spec.weather },
      ],
      { headers: { 'Content-Type': 'application/json-patch+json' } }
    )
  } else {
    await axios.post(journalApi, payload)
  }
}

async function createJournalDraft() {
  const title = normalize(form.value.title) || selectedPhoto.value?.spec.displayName || '新的日记'
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const content = [
      selectedPhoto.value?.spec.url
        ? `![${selectedPhoto.value.spec.displayName || title}](${selectedPhoto.value.spec.url})`
        : '',
      '',
      form.value.teaser || selectedPhoto.value?.spec.description || '',
    ]
      .filter(Boolean)
      .join('\n')
    const { data: singlePage } = await axios.post(consoleSinglePageApi, {
      singlePage: {
        apiVersion: 'content.halo.run/v1alpha1',
        kind: 'SinglePage',
        metadata: {
          name: '',
          generateName: 'singlepage-',
          annotations: {
            'photo-story-linker.xiongfan.me/type': 'journal',
            'photo-story-linker.xiongfan.me/photo-name': selectedPhotoName.value || '',
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
          visible: form.value.visible,
          cover: selectedPhoto.value?.spec.url || selectedPhoto.value?.spec.cover || '',
          excerpt: {
            autoGenerate: !normalize(form.value.teaser),
            raw: normalize(form.value.teaser) || undefined,
          },
        },
      },
      content: {
        rawType: 'markdown',
        raw: content,
        content,
      },
    })
    form.value.targetKind = 'SINGLE_PAGE'
    form.value.targetName = singlePage.metadata.name
    await upsertJournalEntry(singlePage.metadata.name)
    if (selectedPhotoName.value) {
      await upsertBinding('SINGLE_PAGE', singlePage.metadata.name)
    }
    window.location.href = editUrl('SINGLE_PAGE', singlePage.metadata.name)
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    saving.value = false
  }
}

async function saveExistingTarget() {
  if (!form.value.targetName) {
    error.value = '请选择要绑定或加入日记列表的内容。'
    return
  }
  if (form.value.targetKind === 'POST' && !selectedPhotoName.value) {
    error.value = '文章只能作为图库图片的故事目标，请先选择一张图片。'
    return
  }
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    if (selectedPhotoName.value) {
      await upsertBinding(form.value.targetKind, form.value.targetName)
    }
    if (form.value.targetKind === 'SINGLE_PAGE') {
      await upsertJournalEntry(form.value.targetName)
    }
    notice.value = selectedPhotoName.value ? '已保存绑定。' : '已加入日记列表。'
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
  try {
    await axios.patch(
      `${bindingApi}/${binding.metadata.name}`,
      [{ op: 'add', path: '/spec/enabled', value: binding.spec.enabled === false }],
      { headers: { 'Content-Type': 'application/json-patch+json' } }
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
    notice.value = '已解除绑定，图片和内容都不会被删除。'
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
        <h1>日记故事</h1>
        <p>日记内容保存为 Halo 单页面，图库图片可以作为可选入口。</p>
      </div>
      <div class="psl-header-actions">
        <a class="psl-link-button" href="/journals" target="_blank" rel="noreferrer">查看日记页</a>
        <button type="button" class="psl-button psl-button-secondary" :disabled="loading" @click="fetchAll">
          {{ loading ? '加载中...' : '刷新' }}
        </button>
      </div>
    </header>

    <section class="psl-stats">
      <div>
        <strong>{{ stats.journals }}</strong>
        <span>日记</span>
      </div>
      <div>
        <strong>{{ stats.bindings }}</strong>
        <span>图片绑定</span>
      </div>
      <div>
        <strong>{{ stats.photos }}</strong>
        <span>图库图片</span>
      </div>
    </section>

    <p v-if="error" class="psl-message psl-error">{{ error }}</p>
    <p v-if="notice" class="psl-message psl-notice">{{ notice }}</p>

    <section class="psl-workspace">
      <aside class="psl-panel psl-photo-panel">
        <div class="psl-panel-head">
          <h2>关联图片</h2>
          <input v-model="query" type="search" placeholder="搜索图片、描述、分组或标签" />
        </div>

        <button
          type="button"
          class="psl-photo-item"
          :class="{ 'is-active': !selectedPhotoName }"
          @click="selectNoPhoto"
        >
          <span class="psl-photo-placeholder">无</span>
          <span>
            <strong>不绑定图片</strong>
            <small>创建独立日记</small>
          </span>
        </button>

        <div v-if="!photos.length && !loading" class="psl-empty">未检测到图库图片。</div>
        <div v-else class="psl-photo-list">
          <button
            v-for="photo in filteredPhotos"
            :key="photo.metadata.name"
            type="button"
            class="psl-photo-item"
            :class="{ 'is-active': selectedPhotoName === photo.metadata.name }"
            @click="selectPhoto(photo)"
          >
            <img :src="photo.spec.cover || photo.spec.url || ''" :alt="photo.spec.displayName || photo.metadata.name" />
            <span>
              <strong>{{ photo.spec.displayName || photo.metadata.name }}</strong>
              <small>{{ bindingByPhotoName.has(photo.metadata.name) ? '已绑定' : '未绑定' }}</small>
            </span>
          </button>
        </div>
      </aside>

      <section class="psl-panel psl-editor-panel">
        <div class="psl-panel-head">
          <h2>{{ selectedPhoto ? '为图片创建/绑定日记' : '创建日记' }}</h2>
          <span v-if="selectedBinding" class="psl-pill">当前图片已绑定</span>
        </div>

        <div class="psl-selected-photo">
          <img
            v-if="selectedPhoto"
            :src="selectedPhoto.spec.cover || selectedPhoto.spec.url || ''"
            :alt="selectedPhoto.spec.displayName || selectedPhoto.metadata.name"
          />
          <div v-else class="psl-photo-placeholder psl-large">无</div>
          <div>
            <strong>{{ selectedPhoto ? photoTitle(selectedPhoto.metadata.name) : '不绑定图片' }}</strong>
            <p>{{ selectedPhoto?.spec.description || '日记可以独立创建，也可以关联一张图库图片作为入口。' }}</p>
          </div>
        </div>

        <div class="psl-tabs" role="tablist" aria-label="日记操作模式">
          <button type="button" :class="{ 'is-active': mode === 'journal' }" @click="mode = 'journal'">
            创建日记
          </button>
          <button type="button" :class="{ 'is-active': mode === 'existing' }" @click="mode = 'existing'">
            绑定已有内容
          </button>
        </div>

        <form v-if="mode === 'journal'" class="psl-form" @submit.prevent="createJournalDraft">
          <label>
            <span>日记标题</span>
            <input v-model="form.title" type="text" placeholder="例如：一次黄昏里的火箭发射" />
          </label>

          <div class="psl-two">
            <label>
              <span>日记日期</span>
              <input v-model="form.journalDate" type="date" />
            </label>
            <label>
              <span>可见性</span>
              <select v-model="form.visible">
                <option value="PUBLIC">公开</option>
                <option value="INTERNAL">登录可见</option>
                <option value="PRIVATE">私密</option>
              </select>
            </label>
          </div>

          <label>
            <span>摘要/悬停预览文案</span>
            <textarea v-model="form.teaser" rows="4" placeholder="会写入日记摘要，也会作为图片悬停预览。"></textarea>
          </label>

          <div class="psl-two">
            <label>
              <span>心情</span>
              <input v-model="form.mood" type="text" placeholder="例如：期待" />
            </label>
            <label>
              <span>地点</span>
              <input v-model="form.location" type="text" placeholder="例如：广州" />
            </label>
          </div>

          <label>
            <span>天气</span>
            <input v-model="form.weather" type="text" placeholder="例如：晴" />
          </label>

          <label class="psl-check">
            <input v-model="form.showInJournalList" type="checkbox" />
            <span>展示在 /journals 日记列表</span>
          </label>

          <div class="psl-actions">
            <button type="submit" class="psl-button" :disabled="saving">创建日记并编辑</button>
          </div>
        </form>

        <form v-else class="psl-form" @submit.prevent="saveExistingTarget">
          <div class="psl-two">
            <label>
              <span>内容类型</span>
              <select v-model="form.targetKind" @change="targetOptionsChanged">
                <option value="SINGLE_PAGE">日记/页面</option>
                <option value="POST">文章</option>
              </select>
            </label>
            <label>
              <span>搜索内容</span>
              <input v-model="targetQuery" type="search" placeholder="输入标题或链接过滤" />
            </label>
          </div>

          <label>
            <span>绑定目标</span>
            <select v-model="form.targetName">
              <option v-for="item in filteredTargets" :key="item.metadata.name" :value="item.metadata.name">
                {{ item.spec.title || item.metadata.name }} - {{ contentStatus(item) }}
              </option>
            </select>
          </label>

          <label>
            <span>悬停预览文案</span>
            <textarea v-model="form.teaser" rows="4" placeholder="留空时使用目标内容摘要。"></textarea>
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

          <label v-if="form.targetKind === 'SINGLE_PAGE'" class="psl-check">
            <input v-model="form.showInJournalList" type="checkbox" />
            <span>展示在 /journals 日记列表</span>
          </label>

          <div class="psl-actions">
            <button type="submit" class="psl-button" :disabled="saveExistingDisabled">保存</button>
            <a v-if="selectedTarget" class="psl-link-button" :href="editUrl(form.targetKind, selectedTarget.metadata.name)">
              编辑内容
            </a>
          </div>
        </form>
      </section>

      <aside class="psl-panel psl-bindings-panel">
        <div class="psl-panel-head">
          <h2>当前日记</h2>
        </div>

        <div v-if="!journals.length" class="psl-empty">还没有日记索引。</div>
        <article v-for="journal in journals" :key="journal.metadata.name" class="psl-binding">
          <div>
            <strong>{{ targetTitle('SINGLE_PAGE', journal.spec.singlePageName) }}</strong>
            <span>{{ journal.spec.journalDate || '未设置日期' }}</span>
            <small>{{ journal.spec.showInJournalList === false ? '不进列表' : '进入列表' }}</small>
          </div>
          <a class="psl-mini" :href="editUrl('SINGLE_PAGE', journal.spec.singlePageName)">编辑</a>
        </article>

        <div class="psl-panel-head psl-secondary-head">
          <h2>图片绑定</h2>
        </div>
        <div v-if="!bindings.length" class="psl-empty">还没有图片绑定。</div>
        <article v-for="binding in bindings" :key="binding.metadata.name" class="psl-binding">
          <div>
            <strong>{{ photoTitle(binding.spec.photoName) }}</strong>
            <span>
              {{
                targetTitle(
                  resolveBindingTarget(binding).targetKind,
                  resolveBindingTarget(binding).targetName
                )
              }}
            </span>
            <small>{{ binding.spec.enabled === false ? '已停用' : '启用中' }}</small>
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
.psl-header-actions,
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

.psl-header-actions {
  flex-wrap: wrap;
  align-items: center;
}

.psl-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.psl-secondary-head {
  margin-top: 0.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
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
  grid-template-columns: 4.5rem minmax(0, 1fr);
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
.psl-selected-photo img,
.psl-photo-placeholder {
  width: 100%;
  height: 100%;
  border-radius: 0.375rem;
  object-fit: cover;
  background: #e5e7eb;
}

.psl-photo-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-weight: 800;
}

.psl-photo-placeholder.psl-large {
  min-height: 6rem;
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

.psl-selected-photo,
.psl-form {
  display: grid;
  gap: 1rem;
}

.psl-selected-photo {
  grid-template-columns: 6rem minmax(0, 1fr);
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

.psl-check {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
}

.psl-check input {
  width: 1rem;
  min-height: 1rem;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 0.75rem;
  background: #f3f4f6;
  color: #111827;
  text-decoration: none;
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
    grid-template-columns: minmax(18rem, 23rem) minmax(30rem, 1fr) minmax(18rem, 24rem);
    align-items: start;
  }
}
</style>
