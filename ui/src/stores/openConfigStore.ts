///
/// Licensed to The OpenNMS Group, Inc (TOG) under one or more
/// contributor license agreements.  See the LICENSE.md file
/// distributed with this work for additional information
/// regarding copyright ownership.
///
/// TOG licenses this file to You under the GNU Affero General
/// Public License Version 3 (the "License") or (at your option)
/// any later version.  You may not use this file except in
/// compliance with the License.  You may obtain a copy of the
/// License at:
///
///      https://www.gnu.org/licenses/agpl-3.0.txt
///
/// Unless required by applicable law or agreed to in writing,
/// software distributed under the License is distributed on an
/// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
/// either express or implied.  See the License for the specific
/// language governing permissions and limitations under the
/// License.
///

import { defineStore } from 'pinia'
import {
  createProfile,
  createTarget,
  deleteProfiles,
  deleteTargets,
  getProfiles,
  getTargets,
  setProfilesEnabled,
  updateProfile,
  updateTarget,
  OpenConfigProfile,
  OpenConfigTarget
} from '@/services/openConfigService'
import { getModelSets, YangModelSet } from '@/services/yangRegistryService'
import { addCredentials, getAliases } from '@/services/scvService'

// A "Metric Set" is a reusable subscription profile (what/how to collect).
export type MetricSet = OpenConfigProfile

// A raw target carrying its owning metric set, used to assemble device-centric rows.
interface FlatTarget extends OpenConfigTarget {
  metricSetId: number
  metricSetName: string
}

// One metric set applied to a device.
export interface DeviceMetricSet {
  metricSetId: number
  metricSetName: string
  targetId: number
  enabled: boolean
}

// A monitored device = a node with connection details and 1..N metric sets.
export interface Device {
  nodeId: string
  label: string
  port?: number
  credentialRef?: string
  metricSets: DeviceMetricSet[]
}

export interface SaveDeviceSpec {
  nodeId: string
  label: string
  port?: number
  credentialRef?: string
  metricSetIds: number[]
}

export const useOpenConfigStore = defineStore('openConfigStore', () => {
  const metricSets = ref([] as MetricSet[])
  const flatTargets = ref([] as FlatTarget[])
  const modelSets = ref([] as YangModelSet[])
  const credentialAliases = ref([] as string[])

  // Device-centric rollup: group node-matched targets by node.
  const devices = computed<Device[]>(() => {
    const byNode = new Map<string, Device>()
    for (const t of flatTargets.value) {
      if (t.matchType !== 'NODES' || !t.nodeIds) {
        continue
      }
      const key = t.nodeIds.trim()
      let dev = byNode.get(key)
      if (!dev) {
        dev = { nodeId: key, label: t.name || key, port: t.port, credentialRef: t.credentialRef, metricSets: [] }
        byNode.set(key, dev)
      }
      dev.metricSets.push({
        metricSetId: t.metricSetId,
        metricSetName: t.metricSetName,
        targetId: t.id as number,
        enabled: !!t.enabled
      })
    }
    return Array.from(byNode.values())
  })

  const loadMetricSets = async () => {
    metricSets.value = await getProfiles()
  }
  const loadModelSets = async () => {
    modelSets.value = await getModelSets()
  }
  const loadCredentialAliases = async () => {
    credentialAliases.value = await getAliases()
  }

  const loadTargets = async () => {
    await loadMetricSets()
    const all: FlatTarget[] = []
    for (const ms of metricSets.value) {
      if (ms.id == null) {
        continue
      }
      const targets = await getTargets(ms.id)
      for (const t of targets) {
        all.push({ ...t, metricSetId: ms.id, metricSetName: ms.name })
      }
    }
    flatTargets.value = all
  }

  const loadAll = async () => {
    await Promise.all([loadTargets(), loadModelSets(), loadCredentialAliases()])
  }

  // ---- Metric sets (profiles) ----
  const saveMetricSet = async (ms: MetricSet) => {
    const ok = ms.id ? await updateProfile(ms.id, ms) : (await createProfile(ms)) != null
    if (ok) {
      await loadAll()
    }
    return ok
  }
  const removeMetricSet = async (id: number) => {
    if (await deleteProfiles([id])) {
      await loadAll()
    }
  }
  const toggleMetricSet = async (id: number, enabled: boolean) => {
    if (await setProfilesEnabled([id], enabled)) {
      await loadAll()
    }
  }

  // ---- Credentials (Secure Credentials Vault) ----
  const createCredential = async (alias: string, username: string, password: string) => {
    const status = await addCredentials({ alias, username, password, attributes: {} } as any)
    await loadCredentialAliases()
    return status != null
  }

  // ---- Devices (reconcile targets per node) ----
  const saveDevice = async (spec: SaveDeviceSpec) => {
    const existing = new Map<number, FlatTarget>()
    for (const t of flatTargets.value) {
      if (t.matchType === 'NODES' && t.nodeIds?.trim() === spec.nodeId) {
        existing.set(t.metricSetId, t)
      }
    }
    // Create/update a target per selected metric set.
    for (const msId of spec.metricSetIds) {
      const cur = existing.get(msId)
      const target: OpenConfigTarget = {
        id: cur?.id,
        name: spec.label,
        matchType: 'NODES',
        nodeIds: spec.nodeId,
        port: spec.port,
        credentialRef: spec.credentialRef,
        enabled: true
      }
      if (cur) {
        await updateTarget(msId, cur.id as number, target)
      } else {
        await createTarget(msId, target)
      }
    }
    // Remove metric sets that were unchecked.
    for (const [msId, t] of existing) {
      if (!spec.metricSetIds.includes(msId)) {
        await deleteTargets(msId, [t.id as number])
      }
    }
    await loadTargets()
    return true
  }

  const removeDevice = async (device: Device) => {
    for (const m of device.metricSets) {
      await deleteTargets(m.metricSetId, [m.targetId])
    }
    await loadTargets()
  }

  return {
    metricSets,
    devices,
    modelSets,
    credentialAliases,
    loadAll,
    loadTargets,
    loadMetricSets,
    loadModelSets,
    loadCredentialAliases,
    saveMetricSet,
    removeMetricSet,
    toggleMetricSet,
    createCredential,
    saveDevice,
    removeDevice
  }
})
