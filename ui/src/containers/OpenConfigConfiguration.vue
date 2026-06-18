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
  <div class="openconfig-container">
    <div class="feather-row">
      <div class="feather-col-12">
        <BreadCrumbs :items="breadcrumbs" />
      </div>
    </div>

    <div class="header">
      <div class="heading">
        <h1>Streaming Telemetry (OpenConfig)</h1>
        <p class="subtitle">
          Monitor devices over gNMI/OpenConfig: pick the metrics you want and point them at a device.
        </p>
      </div>
      <router-link class="advanced-link" to="/yang-models">
        <FeatherIcon :icon="SettingsIcon" /> YANG Models (advanced)
      </router-link>
    </div>

    <section class="how-it-works">
      <div class="hiw-title"><FeatherIcon :icon="InfoIcon" /> How to monitor a device</div>
      <ol class="hiw-steps">
        <li>
          <strong>Provision the device</strong> — it must be a node in OpenNMS, reachable over
          gNMI/gRPC, with a monitored service named <code>OpenConfig</code>.
        </li>
        <li>
          <strong>Add the device</strong> — search for it by name, choose a stored credential set
          (or add one), and tick the <strong>metric sets</strong> you want to collect from it.
        </li>
        <li>
          <strong>Collect</strong> — telemetryd streams those metrics and stores them against the
          node. A device can carry several metric sets (interfaces, BGP, system, …).
        </li>
      </ol>
    </section>

    <div class="body">
      <FeatherTabContainer v-model="activeTab">
        <template v-slot:tabs>
          <FeatherTab>Monitored Devices</FeatherTab>
          <FeatherTab>Metric Sets</FeatherTab>
        </template>

        <!-- ===================== Devices ===================== -->
        <FeatherTabPanel>
          <TableCard class="oc-table-card">
            <div class="card-header">
              <h2>Monitored Devices <span class="count">({{ store.devices.length }})</span></h2>
              <FeatherButton
                primary
                :disabled="store.metricSets.length === 0"
                data-test="add-device"
                @click="openDeviceDialog()"
              >
                <FeatherIcon :icon="AddIcon" /> Add Device
              </FeatherButton>
            </div>
            <div class="container">
              <table class="data-table" v-if="store.devices.length">
                <thead>
                  <tr>
                    <th>Device</th>
                    <th>Metric Sets</th>
                    <th>Port</th>
                    <th>Credentials</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="d in store.devices" :key="d.nodeId">
                    <td>
                      <div class="device-cell">
                        <strong>{{ d.label }}</strong>
                        <span class="muted">node {{ d.nodeId }}</span>
                      </div>
                    </td>
                    <td>
                      <div class="chips">
                        <FeatherChip v-for="m in d.metricSets" :key="m.targetId" class="ms-chip">
                          {{ m.metricSetName }}
                        </FeatherChip>
                      </div>
                    </td>
                    <td>{{ d.port || '—' }}</td>
                    <td>{{ d.credentialRef || '—' }}</td>
                    <td>
                      <div class="action-container">
                        <FeatherButton icon="Edit device" @click="openDeviceDialog(d)">
                          <FeatherIcon :icon="EditIcon" />
                        </FeatherButton>
                        <FeatherButton icon="Remove device" @click="askDeleteDevice(d)">
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
                  title: store.metricSets.length ? 'No devices monitored yet' : 'Create a metric set first',
                  msg: store.metricSets.length
                    ? 'Click Add Device, search for it by name, choose credentials, and tick the metric sets to collect.'
                    : 'A device needs at least one metric set (what to collect). Switch to the Metric Sets tab — starters referencing the seeded models are there.',
                  btn: store.metricSets.length
                    ? { label: 'Add Device', action: () => openDeviceDialog() }
                    : { label: 'Go to Metric Sets', action: () => (activeTab = 1) }
                }"
              />
            </div>
          </TableCard>
        </FeatherTabPanel>

        <!-- ===================== Metric Sets ===================== -->
        <FeatherTabPanel>
          <TableCard class="oc-table-card">
            <div class="card-header">
              <h2>Metric Sets <span class="count">({{ store.metricSets.length }})</span></h2>
              <FeatherButton primary data-test="add-metric-set" @click="openMetricSetDialog()">
                <FeatherIcon :icon="AddIcon" /> Add Metric Set
              </FeatherButton>
            </div>
            <div class="container">
              <table class="data-table" v-if="store.metricSets.length">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Transport</th>
                    <th>Interval</th>
                    <th>Decoded by</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="m in store.metricSets" :key="m.id">
                    <td>
                      <div class="device-cell">
                        <strong>{{ m.name }}</strong>
                        <span class="muted">{{ m.description }}</span>
                      </div>
                    </td>
                    <td>{{ transportLabel(m.transport) }}</td>
                    <td>{{ intervalLabel(m.sampleInterval) }}</td>
                    <td>{{ decodingLabel(m) }}</td>
                    <td>
                      <FeatherChip :class="m.enabled ? 'enabled-tag' : 'disabled-tag'">
                        {{ m.enabled ? 'Enabled' : 'Disabled' }}
                      </FeatherChip>
                    </td>
                    <td>
                      <div class="action-container">
                        <FeatherButton icon="Edit metric set" @click="openMetricSetDialog(m)">
                          <FeatherIcon :icon="EditIcon" />
                        </FeatherButton>
                        <FeatherDropdown>
                          <template v-slot:trigger="{ attrs, on }">
                            <FeatherButton link href="#" v-bind="attrs" v-on="on" :icon="`More actions for ${m.name}`">
                              <FeatherIcon :icon="MenuIcon" />
                            </FeatherButton>
                          </template>
                          <FeatherDropdownItem @click="cloneMetricSet(m)">Duplicate</FeatherDropdownItem>
                          <FeatherDropdownItem @click="store.toggleMetricSet(m.id as number, !m.enabled)">
                            {{ m.enabled ? 'Disable' : 'Enable' }}
                          </FeatherDropdownItem>
                          <FeatherDropdownItem @click="askDeleteMetricSet(m)">Delete</FeatherDropdownItem>
                        </FeatherDropdown>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <EmptyList
                v-else
                :content="{
                  title: 'No metric sets yet',
                  msg: 'A metric set bundles what to collect (gNMI paths), how often, and which YANG model decodes it. Create one, then attach it to devices.',
                  btn: { label: 'Add Metric Set', action: () => openMetricSetDialog() }
                }"
              />
            </div>
          </TableCard>
        </FeatherTabPanel>
      </FeatherTabContainer>
    </div>

    <!-- ===================== Add/Edit Device ===================== -->
    <FeatherDialog v-model="showDeviceDialog" :labels="{ title: editingDevice ? 'Edit Device' : 'Add Device' }">
      <div class="dialog-form">
        <p class="section-label">Device</p>
        <FeatherAutocomplete
          v-if="!editingDevice"
          label="Search device by name"
          type="single"
          text-prop="_text"
          :loading="nodeLoading"
          :results="nodeResults"
          v-model="selectedNode"
          :allow-new="false"
          @search="onNodeSearch"
          @update:modelValue="onNodePicked"
        />
        <div v-else class="fixed-device">
          <strong>{{ deviceForm.label }}</strong> <span class="muted">node {{ deviceForm.nodeId }}</span>
        </div>

        <p class="section-label">Connection</p>
        <FeatherInput label="gNMI port" type="number" v-model.number="deviceForm.port" />
        <FeatherSelect
          label="Credentials"
          textProp="name"
          :options="(credOptions as unknown as ISelectItemType[])"
          v-model="credModel"
        />
        <template v-if="deviceForm.credentialRef === NEW_CRED">
          <FeatherInput label="Credential name (alias)" v-model.trim="newCred.alias" hint="Stored in the Secure Credentials Vault for reuse" />
          <FeatherInput label="Username" v-model.trim="newCred.username" />
          <FeatherInput label="Password" type="password" v-model="newCred.password" />
        </template>

        <p class="section-label">Metric sets to collect</p>
        <div v-if="store.metricSets.length" class="ms-picker">
          <FeatherCheckbox
            v-for="m in store.metricSets"
            :key="m.id"
            :modelValue="selectedMetricSetIds.includes(m.id as number)"
            @update:modelValue="(v: unknown) => toggleMetricSetSel(m.id as number, !!v)"
          >
            {{ m.name }}
          </FeatherCheckbox>
        </div>
      </div>
      <template v-slot:footer>
        <FeatherButton primary :disabled="!canSaveDevice" @click="saveDevice">Save</FeatherButton>
        <FeatherButton @click="showDeviceDialog = false">Cancel</FeatherButton>
      </template>
    </FeatherDialog>

    <!-- ===================== Add/Edit Metric Set ===================== -->
    <FeatherDialog v-model="showMetricSetDialog" :labels="{ title: metricSetForm.id ? 'Edit Metric Set' : 'New Metric Set' }">
      <div class="dialog-form">
        <FeatherInput label="Name" v-model.trim="metricSetForm.name" data-test="metric-set-name" />
        <FeatherInput label="Description" v-model.trim="metricSetForm.description" />
        <FeatherSelect
          label="Transport"
          textProp="name"
          :options="(transportOptions as unknown as ISelectItemType[])"
          v-model="msTransportModel"
        />
        <FeatherSelect
          label="Decoded by (YANG model set)"
          textProp="name"
          :options="(modelSetOptions as unknown as ISelectItemType[])"
          v-model="modelSetModel"
        />
        <FeatherSelect
          label="Encoding"
          textProp="name"
          :options="(encodingOptions as unknown as ISelectItemType[])"
          v-model="msEncodingModel"
        />
        <FeatherInput label="Sample interval (ns for gNMI, ms for JTI)" type="number" v-model.number="metricSetForm.sampleInterval" />
        <FeatherInput label="RRD step (s)" type="number" v-model.number="metricSetForm.rrdStep" />
        <FeatherTextarea label="Paths (comma-separated gNMI paths)" v-model.trim="metricSetForm.paths" />
        <FeatherCheckbox :modelValue="metricSetForm.enabled" @update:modelValue="(v: unknown) => metricSetForm.enabled = !!v">
          Enabled
        </FeatherCheckbox>
      </div>
      <template v-slot:footer>
        <FeatherButton primary :disabled="!metricSetForm.name" @click="saveMetricSet">Save</FeatherButton>
        <FeatherButton @click="showMetricSetDialog = false">Cancel</FeatherButton>
      </template>
    </FeatherDialog>

    <ConfirmationDialog
      :visible="showDeleteDialog"
      :title="deleteKind === 'device' ? 'Remove Device' : 'Delete Metric Set'"
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
import { Device, MetricSet, useOpenConfigStore } from '@/stores/openConfigStore'
import { useMenuStore } from '@/stores/menuStore'
import { OpenConfigProfile } from '@/services/openConfigService'
import { getNodes } from '@/services/nodeService'
import { BreadCrumb } from '@/types'
import { FeatherAutocomplete } from '@featherds/autocomplete'
import { FeatherButton } from '@featherds/button'
import { FeatherChip } from '@featherds/chips'
import { FeatherCheckbox } from '@featherds/checkbox'
import { FeatherDialog } from '@featherds/dialog'
import { FeatherDropdown, FeatherDropdownItem } from '@featherds/dropdown'
import { FeatherIcon } from '@featherds/icon'
import { FeatherInput } from '@featherds/input'
import { FeatherSelect, ISelectItemType } from '@featherds/select'
import { FeatherTab, FeatherTabContainer, FeatherTabPanel } from '@featherds/tabs'
import { FeatherTextarea } from '@featherds/textarea'
import AddIcon from '@featherds/icon/action/Add'
import DeleteIcon from '@featherds/icon/action/Delete'
import EditIcon from '@featherds/icon/action/Edit'
import InfoIcon from '@featherds/icon/action/Info'
import SettingsIcon from '@featherds/icon/action/Settings'
import MenuIcon from '@featherds/icon/navigation/MoreHoriz'

interface Option { name: string; value: string }
const NEW_CRED = '__new_cred'

const store = useOpenConfigStore()
const menuStore = useMenuStore()
const activeTab = ref(0)

const homeUrl = computed<string>(() => menuStore.mainMenu?.homeUrl)
const breadcrumbs = computed<BreadCrumb[]>(() => [
  { label: 'Home', to: homeUrl.value, isAbsoluteLink: true },
  { label: 'Streaming Telemetry (OpenConfig)', to: '#', position: 'last' }
])

const transportOptions: Option[] = [
  { name: 'gNMI dial-in', value: 'GNMI_DIALIN' },
  { name: 'JTI', value: 'JTI' }
]
const encodingOptions: Option[] = [
  { name: 'PROTO', value: 'PROTO' },
  { name: 'JSON_IETF', value: 'JSON_IETF' }
]
const transportLabel = (v?: string) => transportOptions.find((o) => o.value === v)?.name || v || '—'
const intervalLabel = (ns?: number) => (ns == null ? '—' : `${ns}`)
const decodingLabel = (m?: MetricSet) =>
  m && m.modelSet && m.modelSet.trim() ? `YANG: ${m.modelSet}` : 'Groovy script'

const modelSetOptions = computed<Option[]>(() => [
  { name: '(none — use Groovy script)', value: '' },
  ...store.modelSets.map((m) => ({ name: m.name, value: m.name }))
])
const credOptions = computed<Option[]>(() => [
  ...store.credentialAliases.map((a) => ({ name: a, value: a })),
  { name: '+ Add new credentials…', value: NEW_CRED }
])

const optModel = (options: () => Option[], get: () => string | undefined, set: (v?: string) => void) =>
  computed<ISelectItemType | undefined>({
    get: () => options().find((o) => o.value === (get() ?? '')) as unknown as ISelectItemType | undefined,
    set: (o) => set((o as unknown as Option | undefined)?.value)
  })

// ---- Device dialog ----
const showDeviceDialog = ref(false)
const editingDevice = ref(false)
const nodeResults = ref([] as any[])
const nodeLoading = ref(false)
const selectedNode = ref<any>(null)
const selectedMetricSetIds = ref([] as number[])
const newCred = reactive({ alias: '', username: '', password: '' })
const deviceForm = reactive({
  nodeId: '',
  label: '',
  port: 9000 as number | undefined,
  credentialRef: '' as string
})

const credModel = optModel(() => credOptions.value, () => deviceForm.credentialRef, (v) => (deviceForm.credentialRef = v || ''))

const onNodeSearch = async (term: string) => {
  if (!term || term.trim().length < 2) {
    nodeResults.value = []
    return
  }
  nodeLoading.value = true
  try {
    const resp = await getNodes({ _s: `label==*${term.trim()}*`, limit: 25 })
    const nodes = resp && (resp as any).node ? (resp as any).node : []
    nodeResults.value = nodes.map((n: any) => ({ _text: n.label, id: String(n.id) }))
  } finally {
    nodeLoading.value = false
  }
}
const onNodePicked = (item: any) => {
  deviceForm.nodeId = item?.id || ''
  deviceForm.label = item?._text || ''
}

const canSaveDevice = computed(() => !!deviceForm.nodeId && selectedMetricSetIds.value.length > 0)

const toggleMetricSetSel = (id: number, on: boolean) => {
  selectedMetricSetIds.value = on
    ? [...selectedMetricSetIds.value, id]
    : selectedMetricSetIds.value.filter((x) => x !== id)
}

const openDeviceDialog = (d?: Device) => {
  selectedNode.value = null
  Object.assign(newCred, { alias: '', username: '', password: '' })
  if (d) {
    editingDevice.value = true
    Object.assign(deviceForm, { nodeId: d.nodeId, label: d.label, port: d.port ?? 9000, credentialRef: d.credentialRef || '' })
    selectedMetricSetIds.value = d.metricSets.map((m) => m.metricSetId)
  } else {
    editingDevice.value = false
    Object.assign(deviceForm, { nodeId: '', label: '', port: 9000, credentialRef: '' })
    selectedMetricSetIds.value = []
  }
  showDeviceDialog.value = true
}

const saveDevice = async () => {
  let credentialRef = deviceForm.credentialRef
  if (credentialRef === NEW_CRED) {
    if (!newCred.alias || !newCred.username) {
      return
    }
    await store.createCredential(newCred.alias, newCred.username, newCred.password)
    credentialRef = newCred.alias
  }
  const ok = await store.saveDevice({
    nodeId: deviceForm.nodeId,
    label: deviceForm.label,
    port: deviceForm.port,
    credentialRef: credentialRef || undefined,
    metricSetIds: selectedMetricSetIds.value
  })
  if (ok) {
    showDeviceDialog.value = false
  }
}

// ---- Metric set dialog ----
const showMetricSetDialog = ref(false)
const emptyMetricSet = (): MetricSet => ({
  name: '',
  description: '',
  transport: 'GNMI_DIALIN',
  encoding: 'PROTO',
  origin: 'openconfig',
  rrdStep: 300,
  modelSet: '',
  enabled: true
})
const metricSetForm = reactive<MetricSet>(emptyMetricSet())
const msTransportModel = optModel(() => transportOptions, () => metricSetForm.transport, (v) => (metricSetForm.transport = v))
const msEncodingModel = optModel(() => encodingOptions, () => metricSetForm.encoding, (v) => (metricSetForm.encoding = v))
const modelSetModel = optModel(() => modelSetOptions.value, () => metricSetForm.modelSet ?? '', (v) => (metricSetForm.modelSet = v))

const openMetricSetDialog = (m?: OpenConfigProfile) => {
  Object.assign(metricSetForm, emptyMetricSet(), m || {})
  showMetricSetDialog.value = true
}
const cloneMetricSet = (m: MetricSet) => {
  Object.assign(metricSetForm, emptyMetricSet(), m, { id: undefined, name: `${m.name} (copy)` })
  showMetricSetDialog.value = true
}
const saveMetricSet = async () => {
  if (await store.saveMetricSet({ ...metricSetForm })) {
    showMetricSetDialog.value = false
  }
}

// ---- Delete ----
const showDeleteDialog = ref(false)
const deleteKind = ref<'device' | 'metricset'>('device')
const pendingDevice = ref<Device | null>(null)
const pendingMetricSet = ref<MetricSet | null>(null)
const pendingName = computed(() =>
  deleteKind.value === 'device' ? pendingDevice.value?.label : pendingMetricSet.value?.name
)
const askDeleteDevice = (d: Device) => {
  deleteKind.value = 'device'
  pendingDevice.value = d
  showDeleteDialog.value = true
}
const askDeleteMetricSet = (m: MetricSet) => {
  deleteKind.value = 'metricset'
  pendingMetricSet.value = m
  showDeleteDialog.value = true
}
const confirmDelete = async () => {
  showDeleteDialog.value = false
  if (deleteKind.value === 'device' && pendingDevice.value) {
    await store.removeDevice(pendingDevice.value)
  } else if (deleteKind.value === 'metricset' && pendingMetricSet.value?.id) {
    await store.removeMetricSet(pendingMetricSet.value.id)
  }
}

onMounted(() => store.loadAll())
</script>

<style lang="scss" scoped>
@use '@featherds/styles/themes/variables';
@use '@featherds/styles/mixins/typography';
@use '@featherds/table/scss/table';

.openconfig-container {
  padding: 20px;

  .header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 24px 40px 8px 40px;

    .subtitle {
      color: var(--feather-secondary-text-on-surface);
      margin-top: 4px;
    }

    .advanced-link {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      white-space: nowrap;
      font-size: 0.9rem;
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
      margin: 0;
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
  }

  .body {
    padding: 8px 40px 0 40px;
  }

  .oc-table-card {
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

    .container table {
      width: 100%;
      @include table.table;

      thead {
        background: var(variables.$background);
        text-transform: uppercase;
      }

      td {
        border-bottom: 1px solid var(variables.$border-on-surface);
        vertical-align: top;
      }

      .device-cell {
        display: flex;
        flex-direction: column;

        .muted {
          color: var(--feather-secondary-text-on-surface);
          font-size: 0.85em;
        }
      }

      .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;

        .ms-chip {
          margin: 0 !important;
          border-radius: 4px;
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
    min-width: 30em;

    .section-label {
      margin: 8px 0 0 0;
      font-weight: 600;
      font-size: 0.85rem;
      color: var(--feather-secondary-text-on-surface);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .fixed-device .muted {
      color: var(--feather-secondary-text-on-surface);
    }

    .ms-picker {
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-height: 200px;
      overflow-y: auto;
      border: 1px solid var(--feather-border-on-surface);
      border-radius: 4px;
      padding: 8px;
    }
  }
}
</style>
