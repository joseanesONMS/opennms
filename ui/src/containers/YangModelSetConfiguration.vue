<!--
  Licensed to The OpenNMS Group, Inc (TOG) under one or more
  contributor license agreements.  See the LICENSE.md file
  distributed with this work for additional information
  regarding copyright ownership.

  TOG licenses this file to You under the GNU Affero General
  Public License Version 3 (the "License") or (at your option)
  any later version.  You may not use this file except in
  compliance with the License.  You may obtain a copy of the
  License at:

       https://www.gnu.org/licenses/agpl-3.0.txt

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
  either express or implied.  See the License for the specific
  language governing permissions and limitations under the
  License.
-->
<template>
  <div class="yang-registry-container">
    <div class="feather-row">
      <div class="feather-col-12">
        <BreadCrumbs :items="breadcrumbs" />
      </div>
    </div>

    <div class="header">
      <h1>YANG Models <span class="advanced-tag">Advanced</span></h1>
      <p class="subtitle">
        The schema library behind streaming telemetry. Most users won't need this — the starter model
        sets are already here. Come here only to add a new vendor/model/version: upload YANG modules
        and group them into a versioned model set, which a
        <router-link to="/openconfig">template</router-link> then uses to decode telemetry.
      </p>
    </div>

    <section class="how-it-works">
      <div class="hiw-title">
        <FeatherIcon :icon="InfoIcon" /> How it works
      </div>
      <ol class="hiw-steps">
        <li>
          <strong>Upload YANG modules</strong> — the <code>.yang</code> files that describe a device's
          telemetry. Upload each module together with everything it <code>import</code>s (its full
          closure). OpenConfig models live at
          <a href="https://github.com/openconfig/public" target="_blank" rel="noopener">github.com/openconfig/public</a>.
        </li>
        <li>
          <strong>Create a model set</strong> — bundle the modules that form one complete, versioned
          schema (e.g. <em>OpenConfig 2024.x</em>). The set is what gets compiled and cached.
        </li>
        <li>
          <strong>Use it to monitor a device</strong> — go to
          <router-link to="/openconfig">Streaming Telemetry (OpenConfig)</router-link>, create a
          subscription profile and set its <em>YANG model set</em> to this set's name, then add a
          target for the device. telemetryd decodes that device's gNMI into typed metrics from the
          schema — no Groovy script needed.
        </li>
      </ol>
      <p class="hiw-tip">
        <FeatherIcon :icon="InfoIcon" class="tip-icon" />
        A model set must contain its <strong>full import closure</strong> — every module the others
        <code>import</code>/<code>include</code> — or it won't compile.
      </p>
    </section>

    <div class="body">
      <!-- Modules -->
      <TableCard class="yr-table-card">
        <div class="card-header">
          <h2>YANG Modules <span class="count">({{ store.modules.length }})</span></h2>
          <div>
            <input
              ref="fileInput"
              type="file"
              accept=".yang"
              multiple
              style="display: none"
              data-test="yang-file-input"
              @change="onFilesSelected"
            />
            <FeatherButton secondary data-test="upload-yang" @click="triggerUpload">
              <FeatherIcon :icon="AddIcon" /> Upload .yang
            </FeatherButton>
          </div>
        </div>

        <div class="container">
          <div v-if="store.modules.length" class="tree-layout">
            <!-- Sideways tree: functional categories on the left -->
            <aside class="cat-tree">
              <FeatherInput
                class="cat-filter"
                label="Filter categories"
                type="search"
                v-model.trim="categoryFilter"
              >
                <template #pre><FeatherIcon :icon="Search" /></template>
              </FeatherInput>
              <ul class="cat-list">
                <li :class="{ active: selectedCategory === ALL }" @click="selectCategory(ALL)">
                  <span class="cat-name">All categories</span>
                  <span class="cat-count">{{ store.modules.length }}</span>
                </li>
                <li
                  v-for="cat in filteredCategories"
                  :key="cat.name"
                  :class="{ active: selectedCategory === cat.name }"
                  @click="selectCategory(cat.name)"
                >
                  <span class="cat-name">{{ cat.name }}</span>
                  <span class="cat-count">{{ cat.count }}</span>
                </li>
              </ul>
            </aside>

            <!-- Modules in the selected category, paginated -->
            <section class="cat-modules">
              <table class="data-table" aria-label="YANG Modules">
                <thead>
                  <tr>
                    <th>Module</th>
                    <th>Revision</th>
                    <th>OpenConfig version</th>
                    <th>Category</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="module in pagedModules" :key="module.id">
                    <td>{{ module.name }}</td>
                    <td>{{ module.revision || '—' }}</td>
                    <td>{{ module.openconfigVersion || '—' }}</td>
                    <td>{{ categoryOf(module) }}</td>
                    <td>
                      <FeatherButton icon="Delete Module" @click="askDeleteModule(module)">
                        <FeatherIcon :icon="DeleteIcon" />
                      </FeatherButton>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="pager" v-if="modulesInCategory.length > pageSize">
                <FeatherPagination
                  :modelValue="page"
                  :pageSize="pageSize"
                  :total="modulesInCategory.length"
                  :pageSizes="[10, 15, 25, 50]"
                  @update:modelValue="(p: number) => (page = p)"
                  @update:pageSize="(s: number) => { pageSize = s; page = 1 }"
                />
              </div>
            </section>
          </div>
          <EmptyList
            v-else
            :content="{
              title: 'No YANG modules yet',
              msg: 'Upload .yang source files (you can select several at once). Include each module plus everything it imports. OpenConfig models: github.com/openconfig/public.',
              btn: { label: 'Upload .yang', action: triggerUpload }
            }"
          />
        </div>
      </TableCard>

      <!-- Model sets -->
      <TableCard class="yr-table-card">
        <div class="card-header">
          <h2>Model Sets</h2>
          <FeatherButton secondary data-test="add-model-set" @click="openModelSetDialog()">
            <FeatherIcon :icon="AddIcon" /> Add Model Set
          </FeatherButton>
        </div>

        <div class="container">
          <table class="data-table" aria-label="YANG Model Sets" v-if="store.modelSets.length">
            <thead>
              <tr>
                <th>Name</th>
                <th>Version</th>
                <th>Modules</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="set in store.modelSets" :key="set.id">
                <td>{{ set.name }}</td>
                <td>{{ set.version || '—' }}</td>
                <td>{{ set.modules ? set.modules.length : 0 }}</td>
                <td>
                  <FeatherChip :class="set.enabled ? 'enabled-tag' : 'disabled-tag'">
                    {{ set.enabled ? 'Enabled' : 'Disabled' }}
                  </FeatherChip>
                </td>
                <td>
                  <div class="action-container">
                    <FeatherButton icon="Edit Model Set" @click="openModelSetDialog(set)">
                      <FeatherIcon :icon="EditIcon" />
                    </FeatherButton>
                    <FeatherButton icon="Delete Model Set" @click="askDeleteModelSet(set)">
                      <FeatherIcon :icon="DeleteIcon" />
                    </FeatherButton>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <EmptyList
            v-else
            :content="{
              title: 'No model sets yet',
              msg: store.modules.length
                ? 'Group your uploaded modules into a named, versioned set, then reference that name on a subscription profile.'
                : 'Upload modules first, then create a set that bundles them into one schema.',
              btn: store.modules.length ? { label: 'Add Model Set', action: () => openModelSetDialog() } : undefined
            }"
          />
        </div>
      </TableCard>
    </div>

    <!-- Model set create/edit dialog -->
    <FeatherDialog v-model="showDialog" :labels="{ title: form.id ? 'Edit Model Set' : 'New Model Set' }">
      <div class="dialog-form">
        <FeatherInput
          label="Name"
          v-model.trim="form.name"
          hint="Unique name you'll reference on a subscription profile, e.g. openconfig-2024"
          data-test="model-set-name"
        />
        <FeatherInput label="Version" v-model.trim="form.version" hint="Optional, e.g. 2024.x" />
        <FeatherTextarea label="Description" v-model.trim="form.description" />
        <FeatherCheckbox :modelValue="form.enabled" @update:modelValue="(v: unknown) => form.enabled = !!v">
          Enabled
        </FeatherCheckbox>
        <div class="modules-picker">
          <label class="picker-label">Modules (select the full import closure for this set)</label>
          <div v-if="store.modules.length" class="picker-list">
            <FeatherCheckbox
              v-for="module in store.modules"
              :key="module.id"
              :modelValue="isSelected(module.id)"
              @update:modelValue="(v: unknown) => toggleModule(module.id, !!v)"
            >
              {{ module.name }}{{ module.revision ? '@' + module.revision : '' }}
            </FeatherCheckbox>
          </div>
          <p v-else class="muted">Upload modules first to add them to a set.</p>
        </div>
      </div>
      <template v-slot:footer>
        <FeatherButton primary :disabled="!form.name" @click="saveModelSet">Save</FeatherButton>
        <FeatherButton @click="showDialog = false">Cancel</FeatherButton>
      </template>
    </FeatherDialog>

    <ConfirmationDialog
      :visible="showDeleteDialog"
      :title="deleteKind === 'module' ? 'Delete Module' : 'Delete Model Set'"
      actionButtonText="Delete"
      @ok="confirmDelete"
      @cancel="showDeleteDialog = false"
    >
      <template #content>
        <p>Are you sure you want to delete <strong>{{ pendingName }}</strong>? This cannot be undone.</p>
      </template>
    </ConfirmationDialog>
  </div>
</template>

<script lang="ts" setup>
import BreadCrumbs from '@/components/Layout/BreadCrumbs.vue'
import ConfirmationDialog from '@/components/Common/ConfirmationDialog.vue'
import EmptyList from '@/components/Common/EmptyList.vue'
import TableCard from '@/components/Common/TableCard.vue'
import { useYangRegistryStore } from '@/stores/yangRegistryStore'
import { useMenuStore } from '@/stores/menuStore'
import { YangModelSet, YangModule } from '@/services/yangRegistryService'
import { BreadCrumb } from '@/types'
import { FeatherButton } from '@featherds/button'
import { FeatherChip } from '@featherds/chips'
import { FeatherCheckbox } from '@featherds/checkbox'
import { FeatherDialog } from '@featherds/dialog'
import { FeatherIcon } from '@featherds/icon'
import { FeatherInput } from '@featherds/input'
import { FeatherPagination } from '@featherds/pagination'
import { FeatherTextarea } from '@featherds/textarea'
import AddIcon from '@featherds/icon/action/Add'
import DeleteIcon from '@featherds/icon/action/Delete'
import EditIcon from '@featherds/icon/action/Edit'
import InfoIcon from '@featherds/icon/action/Info'
import Search from '@featherds/icon/action/Search'

const store = useYangRegistryStore()
const menuStore = useMenuStore()

const homeUrl = computed<string>(() => menuStore.mainMenu?.homeUrl)
const breadcrumbs = computed<BreadCrumb[]>(() => [
  { label: 'Home', to: homeUrl.value, isAbsoluteLink: true },
  { label: 'YANG Model Sets', to: '#', position: 'last' }
])

// ---- Module category tree + pagination ----
const ALL = '__all'
const categoryFilter = ref('')
const selectedCategory = ref(ALL)
const page = ref(1)
const pageSize = ref(15)

const categoryOf = (m: YangModule): string => {
  const sf = m.sourceFilename || ''
  if (sf.includes('/')) {
    return sf.split('/')[0]
  }
  const match = (m.namespace || '').match(/openconfig\.net\/yang\/([^/"]+)/)
  return match ? match[1] : 'general'
}

const categories = computed(() => {
  const counts: Record<string, number> = {}
  for (const m of store.modules) {
    const c = categoryOf(m)
    counts[c] = (counts[c] || 0) + 1
  }
  return Object.entries(counts)
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => a.name.localeCompare(b.name))
})

const filteredCategories = computed(() => {
  const term = categoryFilter.value.trim().toLowerCase()
  return term ? categories.value.filter((c) => c.name.toLowerCase().includes(term)) : categories.value
})

const modulesInCategory = computed(() => {
  const list =
    selectedCategory.value === ALL
      ? [...store.modules]
      : store.modules.filter((m) => categoryOf(m) === selectedCategory.value)
  return list.sort((a, b) => (a.name || '').localeCompare(b.name || ''))
})

const pagedModules = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return modulesInCategory.value.slice(start, start + pageSize.value)
})

const selectCategory = (name: string) => {
  selectedCategory.value = name
  page.value = 1
}

const fileInput = ref<HTMLInputElement | null>(null)

const triggerUpload = () => fileInput.value?.click()

const onFilesSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  for (const file of files) {
    const content = await file.text()
    await store.uploadModule({ content, sourceFilename: file.name })
  }
  input.value = ''
}

// ---- Model set dialog ----
const showDialog = ref(false)
const emptyForm = (): YangModelSet => ({ name: '', version: '', description: '', enabled: true, moduleIds: [] })
const form = reactive<YangModelSet>(emptyForm())

const isSelected = (id?: number) => !!id && (form.moduleIds || []).includes(id)
const toggleModule = (id: number | undefined, selected: boolean) => {
  if (!id) {
    return
  }
  const ids = form.moduleIds || []
  form.moduleIds = selected ? [...ids, id] : ids.filter((m) => m !== id)
}

const openModelSetDialog = (set?: YangModelSet) => {
  Object.assign(form, emptyForm())
  if (set) {
    Object.assign(form, {
      id: set.id,
      name: set.name,
      version: set.version,
      description: set.description,
      enabled: set.enabled,
      moduleIds: set.modules ? set.modules.map((m) => m.id as number) : (set.moduleIds || [])
    })
  }
  showDialog.value = true
}

const saveModelSet = async () => {
  if (await store.saveModelSet({ ...form })) {
    showDialog.value = false
  }
}

// ---- Delete confirmation ----
const showDeleteDialog = ref(false)
const deleteKind = ref<'module' | 'modelset'>('module')
const pendingModule = ref<YangModule | null>(null)
const pendingModelSet = ref<YangModelSet | null>(null)
const pendingName = computed(() =>
  deleteKind.value === 'module' ? pendingModule.value?.name : pendingModelSet.value?.name
)

const askDeleteModule = (module: YangModule) => {
  deleteKind.value = 'module'
  pendingModule.value = module
  showDeleteDialog.value = true
}
const askDeleteModelSet = (set: YangModelSet) => {
  deleteKind.value = 'modelset'
  pendingModelSet.value = set
  showDeleteDialog.value = true
}
const confirmDelete = async () => {
  showDeleteDialog.value = false
  if (deleteKind.value === 'module' && pendingModule.value?.id) {
    await store.removeModules([pendingModule.value.id])
  } else if (deleteKind.value === 'modelset' && pendingModelSet.value?.id) {
    await store.removeModelSets([pendingModelSet.value.id])
  }
}

onMounted(() => store.loadAll())
</script>

<style lang="scss" scoped>
@use '@featherds/styles/themes/variables';
@use '@featherds/styles/mixins/typography';
@use '@featherds/table/scss/table';

.yang-registry-container {
  padding: 20px;

  .header {
    padding: 24px 40px 8px 40px;

    .advanced-tag {
      display: inline-block;
      vertical-align: middle;
      margin-left: 10px;
      font-size: 0.5em;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--feather-secondary-text-on-surface);
      background: var(--feather-background);
      border: 1px solid var(--feather-border-on-surface);
      border-radius: 10px;
      padding: 2px 10px;
    }

    .subtitle {
      color: var(--feather-secondary-text-on-surface);
      margin-top: 4px;
    }
  }

  .how-it-works {
    margin: 8px 40px 0 40px;
    padding: 16px 20px;
    border: 1px solid var(--feather-border-on-surface);
    border-left: 4px solid var(--feather-primary);
    border-radius: 4px;
    background: var(--feather-background);

    .hiw-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: 600;
      margin-bottom: 8px;

      :deep(svg) {
        fill: var(--feather-primary);
      }
    }

    .hiw-steps {
      margin: 0 0 8px 0;
      padding-left: 22px;

      li {
        margin-bottom: 6px;
        line-height: 1.45;
      }

      code {
        background: var(--feather-surface);
        padding: 0 4px;
        border-radius: 3px;
        font-size: 0.85em;
      }
    }

    .hiw-tip {
      display: flex;
      align-items: flex-start;
      gap: 6px;
      margin: 4px 0 0 0;
      color: var(--feather-secondary-text-on-surface);
      font-size: 0.9em;

      .tip-icon {
        flex: 0 0 auto;
        margin-top: 2px;
        font-size: 1rem;
        :deep(svg) {
          fill: var(--feather-secondary-text-on-surface);
        }
      }

      code {
        background: var(--feather-surface);
        padding: 0 4px;
        border-radius: 3px;
        font-size: 0.85em;
      }
    }
  }

  .body {
    padding: 0 40px;
  }

  .yr-table-card {
    margin-top: 16px;
    padding: 25px;
    border: 1px solid var(--feather-border-on-surface);

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      .count {
        color: var(--feather-secondary-text-on-surface);
        font-weight: 400;
        font-size: 0.8em;
      }
    }

    .tree-layout {
      display: flex;
      gap: 16px;
      align-items: flex-start;

      .cat-tree {
        flex: 0 0 240px;
        max-height: 520px;
        overflow-y: auto;
        border: 1px solid var(--feather-border-on-surface);
        border-radius: 4px;

        .cat-filter {
          padding: 8px 8px 0 8px;

          :deep(.feather-input-sub-text) {
            display: none !important;
          }
        }

        .cat-list {
          list-style: none;
          margin: 4px 0 0 0;
          padding: 0;

          li {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 6px 12px;
            cursor: pointer;
            font-size: 0.9rem;

            &:hover {
              background: var(--feather-background);
            }

            &.active {
              background: var(--feather-background);
              border-left: 3px solid var(--feather-primary);
              font-weight: 600;
            }

            .cat-count {
              color: var(--feather-secondary-text-on-surface);
              font-size: 0.8em;
              background: var(--feather-surface);
              border-radius: 10px;
              padding: 0 8px;
            }
          }
        }
      }

      .cat-modules {
        flex: 1 1 auto;
        min-width: 0;

        .pager {
          display: flex;
          justify-content: center;
          padding-top: 16px;

          :deep(.feather-pagination) {
            border: none !important;
          }
        }
      }
    }

    .container table {
      width: 100%;
      @include table.table;

      thead {
        background: var(variables.$background);
        text-transform: uppercase;
      }

      td {
        white-space: nowrap;
        border-bottom: 1px solid var(variables.$border-on-surface);

        &.truncate {
          max-width: 320px;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .enabled-tag {
        margin: 0 !important;
        border-radius: 4px;
        background-color: #0b720c1f;

        :deep(span) {
          color: #0b720c !important;
        }
      }

      .disabled-tag {
        margin: 0 !important;
        border-radius: 4px;
        background-color: #7575751f;

        :deep(span) {
          color: #757575 !important;
        }
      }

      .action-container {
        display: flex;
        gap: 5px;
      }
    }
  }

  .dialog-form {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 28em;

    .modules-picker {
      .picker-label {
        font-size: 0.85rem;
        display: block;
        margin-bottom: 4px;
      }

      .picker-list {
        max-height: 220px;
        overflow-y: auto;
        border: 1px solid var(--feather-border-on-surface);
        border-radius: 4px;
        padding: 8px;
      }
    }

    .muted {
      color: var(--feather-secondary-text-on-surface);
    }
  }
}
</style>
