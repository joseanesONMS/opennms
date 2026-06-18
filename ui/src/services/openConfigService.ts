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

import useSnackbar from '@/composables/useSnackbar'
import useSpinner from '@/composables/useSpinner'
import { v2 } from './axiosInstances'

const { showSnackBar } = useSnackbar()
const { startSpinner, stopSpinner } = useSpinner()
const endpoint = '/openconfigconf'

export interface OpenConfigProfile {
  id?: number
  name: string
  description?: string
  transport?: string
  encoding?: string
  sampleInterval?: number
  paths?: string
  origin?: string
  rrdStep?: number
  rrdRras?: string
  modelSet?: string
  enabled?: boolean
  uploadedBy?: string
}

export interface OpenConfigTarget {
  id?: number
  profileId?: number
  name: string
  matchType?: string
  filterRule?: string
  nodeIds?: string
  port?: number
  credentialRef?: string
  tlsRef?: string
  enabled?: boolean
  // write-only credential input
  username?: string
  password?: string
}

const getProfiles = async (): Promise<OpenConfigProfile[]> => {
  try {
    startSpinner()
    const resp = await v2.get(`${endpoint}/profiles`)
    return resp.data || []
  } catch (err) {
    showSnackBar({ msg: 'Failed to load OpenConfig profiles.' })
    return []
  } finally {
    stopSpinner()
  }
}

const createProfile = async (profile: OpenConfigProfile): Promise<number | null> => {
  try {
    startSpinner()
    const resp = await v2.post(`${endpoint}/profiles`, profile)
    showSnackBar({ msg: 'Profile created.' })
    return resp.data
  } catch (err) {
    showSnackBar({ msg: 'Failed to create profile.' })
    return null
  } finally {
    stopSpinner()
  }
}

const updateProfile = async (id: number, profile: OpenConfigProfile): Promise<boolean> => {
  try {
    startSpinner()
    await v2.put(`${endpoint}/profiles/${id}`, profile)
    showSnackBar({ msg: 'Profile updated.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to update profile.' })
    return false
  } finally {
    stopSpinner()
  }
}

const deleteProfiles = async (ids: number[]): Promise<boolean> => {
  try {
    startSpinner()
    await v2.delete(`${endpoint}/profiles`, { data: ids })
    showSnackBar({ msg: 'Profile(s) deleted.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to delete profile(s).' })
    return false
  } finally {
    stopSpinner()
  }
}

const setProfilesEnabled = async (ids: number[], enabled: boolean): Promise<boolean> => {
  try {
    startSpinner()
    await v2.put(`${endpoint}/profiles/enable?enabled=${enabled}`, ids)
    showSnackBar({ msg: enabled ? 'Profile(s) enabled.' : 'Profile(s) disabled.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to update profile(s).' })
    return false
  } finally {
    stopSpinner()
  }
}

const getTargets = async (profileId: number): Promise<OpenConfigTarget[]> => {
  try {
    startSpinner()
    const resp = await v2.get(`${endpoint}/profiles/${profileId}/targets`)
    return resp.data || []
  } catch (err) {
    showSnackBar({ msg: 'Failed to load targets.' })
    return []
  } finally {
    stopSpinner()
  }
}

const createTarget = async (profileId: number, target: OpenConfigTarget): Promise<number | null> => {
  try {
    startSpinner()
    const resp = await v2.post(`${endpoint}/profiles/${profileId}/targets`, target)
    showSnackBar({ msg: 'Target created.' })
    return resp.data
  } catch (err) {
    showSnackBar({ msg: 'Failed to create target.' })
    return null
  } finally {
    stopSpinner()
  }
}

const updateTarget = async (profileId: number, targetId: number, target: OpenConfigTarget): Promise<boolean> => {
  try {
    startSpinner()
    await v2.put(`${endpoint}/profiles/${profileId}/targets/${targetId}`, target)
    showSnackBar({ msg: 'Target updated.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to update target.' })
    return false
  } finally {
    stopSpinner()
  }
}

const deleteTargets = async (profileId: number, ids: number[]): Promise<boolean> => {
  try {
    startSpinner()
    await v2.delete(`${endpoint}/profiles/${profileId}/targets`, { data: ids })
    showSnackBar({ msg: 'Target(s) deleted.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to delete target(s).' })
    return false
  } finally {
    stopSpinner()
  }
}

export {
  createProfile,
  createTarget,
  deleteProfiles,
  deleteTargets,
  getProfiles,
  getTargets,
  setProfilesEnabled,
  updateProfile,
  updateTarget
}
