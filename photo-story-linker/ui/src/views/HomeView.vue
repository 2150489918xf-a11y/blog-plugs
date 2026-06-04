<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, ref } from 'vue'

type Metadata = {
  name: string
  creationTimestamp?: string
}

type Photo = {
  metadata: Metadata
  spec: {
    displayName?: string
    url?: string
    cover?: string
    groupName?: string
  }
}

type Post = {
  metadata: Metadata
  spec: {
    title?: string
    publish?: boolean
    deleted?: boolean
    visible?: string
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

const bindings = ref<Binding[]>([])
const photos = ref<Photo[]>([])
const posts = ref<Post[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const form = ref({
  photoName: '',
  postName: '',
  teaser: '',
  badgeText: 'Read story',
  openMode: 'SAME_TAB' as 'SAME_TAB' | 'NEW_TAB',
})

const bindingApi = '/apis/photo-story-linker.xiongfan.me/v1alpha1/photostorybindings'
const photoApi = '/apis/core.halo.run/v1alpha1/photos'
const postApi = '/apis/content.halo.run/v1alpha1/posts'

const availablePosts = computed(() =>
  posts.value.filter((post) => post.spec.publish && !post.spec.deleted && post.spec.visible === 'PUBLIC')
)

const postByName = computed(() => new Map(posts.value.map((post) => [post.metadata.name, post])))
const photoByName = computed(() => new Map(photos.value.map((photo) => [photo.metadata.name, photo])))

async function fetchAll() {
  loading.value = true
  error.value = ''
  try {
    const [bindingRes, photoRes, postRes] = await Promise.all([
      axios.get(bindingApi, { params: { page: 1, size: 200 } }),
      axios.get(photoApi, { params: { page: 1, size: 200 } }),
      axios.get(postApi, { params: { page: 1, size: 200 } }),
    ])
    bindings.value = bindingRes.data.items || []
    photos.value = photoRes.data.items || []
    posts.value = postRes.data.items || []
    if (!form.value.photoName && photos.value.length) {
      form.value.photoName = photos.value[0].metadata.name
    }
    if (!form.value.postName && availablePosts.value.length) {
      form.value.postName = availablePosts.value[0].metadata.name
    }
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    loading.value = false
  }
}

async function createBinding() {
  if (!form.value.photoName || !form.value.postName) {
    error.value = 'Photo and post are required.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const name = `photo-story-${Date.now()}`
    await axios.post(bindingApi, {
      apiVersion: 'photo-story-linker.xiongfan.me/v1alpha1',
      kind: 'PhotoStoryBinding',
      metadata: { name },
      spec: {
        photoName: form.value.photoName,
        postName: form.value.postName,
        teaser: form.value.teaser,
        badgeText: form.value.badgeText || 'Read story',
        enabled: true,
        openMode: form.value.openMode,
      },
    })
    form.value.teaser = ''
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
  try {
    await axios.delete(`${bindingApi}/${binding.metadata.name}`)
    await fetchAll()
  } catch (err) {
    error.value = String((err as Error).message || err)
  } finally {
    saving.value = false
  }
}

function photoTitle(name: string) {
  const photo = photoByName.value.get(name)
  return photo?.spec.displayName || name
}

function postTitle(name: string) {
  const post = postByName.value.get(name)
  return post?.spec.title || name
}

onMounted(fetchAll)
</script>

<template>
  <main class="psl-page">
    <header class="psl-header">
      <div>
        <h1>Photo Stories</h1>
        <p>Bind gallery photos to public Halo posts. Public visitors only receive published public story links.</p>
      </div>
      <button type="button" :disabled="loading" @click="fetchAll">
        {{ loading ? 'Loading...' : 'Refresh' }}
      </button>
    </header>

    <p v-if="error" class="psl-error">{{ error }}</p>

    <section class="psl-grid">
      <form class="psl-panel" @submit.prevent="createBinding">
        <h2>Create binding</h2>

        <label>
          <span>Photo</span>
          <select v-model="form.photoName">
            <option v-for="photo in photos" :key="photo.metadata.name" :value="photo.metadata.name">
              {{ photo.spec.displayName || photo.metadata.name }}
            </option>
          </select>
        </label>

        <label>
          <span>Story post</span>
          <select v-model="form.postName">
            <option v-for="post in availablePosts" :key="post.metadata.name" :value="post.metadata.name">
              {{ post.spec.title || post.metadata.name }}
            </option>
          </select>
        </label>

        <label>
          <span>Teaser override</span>
          <textarea v-model="form.teaser" rows="4" placeholder="Leave empty to use the post excerpt."></textarea>
        </label>

        <div class="psl-two">
          <label>
            <span>Button text</span>
            <input v-model="form.badgeText" type="text" />
          </label>
          <label>
            <span>Open mode</span>
            <select v-model="form.openMode">
              <option value="SAME_TAB">Same tab</option>
              <option value="NEW_TAB">New tab</option>
            </select>
          </label>
        </div>

        <button type="submit" :disabled="saving || !photos.length || !availablePosts.length">
          {{ saving ? 'Saving...' : 'Bind story' }}
        </button>
      </form>

      <section class="psl-panel">
        <h2>Current bindings</h2>
        <div v-if="!bindings.length" class="psl-empty">No bindings yet.</div>
        <article v-for="binding in bindings" :key="binding.metadata.name" class="psl-binding">
          <div>
            <strong>{{ photoTitle(binding.spec.photoName) }}</strong>
            <span>{{ postTitle(binding.spec.postName) }}</span>
            <small>{{ binding.spec.enabled === false ? 'Disabled' : 'Enabled' }}</small>
          </div>
          <button type="button" :disabled="saving" @click="deleteBinding(binding)">Delete</button>
        </article>
      </section>
    </section>
  </main>
</template>

<style scoped>
.psl-page {
  width: 100%;
  min-height: 100%;
  padding: 1rem;
  color: #111827;
}

.psl-header {
  display: grid;
  gap: 1rem;
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

.psl-grid {
  display: grid;
  gap: 1rem;
}

.psl-panel {
  display: grid;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  background: #fff;
}

.psl-two {
  display: grid;
  gap: 1rem;
}

label {
  display: grid;
  gap: 0.375rem;
  font-size: 0.875rem;
  font-weight: 600;
}

input,
select,
textarea {
  width: 100%;
  min-height: 2.75rem;
  padding: 0.625rem 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  background: #fff;
  font: inherit;
}

textarea {
  resize: vertical;
}

button {
  min-height: 2.75rem;
  width: fit-content;
  padding: 0.625rem 0.875rem;
  border: 0;
  border-radius: 0.375rem;
  background: #111827;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.psl-error {
  padding: 0.75rem 1rem;
  border: 1px solid #fecaca;
  border-radius: 0.375rem;
  background: #fef2f2;
  color: #991b1b;
}

.psl-empty {
  color: #6b7280;
}

.psl-binding {
  display: grid;
  gap: 0.75rem;
  padding: 0.875rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
}

.psl-binding div {
  display: grid;
  gap: 0.25rem;
}

.psl-binding span,
.psl-binding small {
  color: #6b7280;
}

@media (min-width: 768px) {
  .psl-page {
    padding: 1.5rem;
  }

  .psl-header {
    grid-template-columns: 1fr auto;
    align-items: start;
  }

  .psl-two,
  .psl-binding {
    grid-template-columns: 1fr 1fr;
    align-items: end;
  }

  .psl-binding {
    grid-template-columns: 1fr auto;
  }
}

@media (min-width: 1024px) {
  .psl-grid {
    grid-template-columns: minmax(20rem, 28rem) minmax(0, 1fr);
    align-items: start;
  }
}
</style>
