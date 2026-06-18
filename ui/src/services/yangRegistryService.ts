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
const endpoint = '/yangregistry'

export interface YangModule {
  id?: number
  name?: string
  revision?: string
  namespace?: string
  openconfigVersion?: string
  sourceFilename?: string
  content?: string
}

export interface YangModelSet {
  id?: number
  name: string
  version?: string
  description?: string
  enabled?: boolean
  moduleIds?: number[]
  modules?: YangModule[]
}

const getModules = async (): Promise<YangModule[]> => {
  try {
    startSpinner()
    const resp = await v2.get(`${endpoint}/modules`)
    return resp.data || []
  } catch (err) {
    showSnackBar({ msg: 'Failed to load YANG modules.' })
    return []
  } finally {
    stopSpinner()
  }
}

const createModule = async (module: YangModule): Promise<number | null> => {
  try {
    startSpinner()
    const resp = await v2.post(`${endpoint}/modules`, module)
    showSnackBar({ msg: 'YANG module stored.' })
    return resp.data
  } catch (err: any) {
    showSnackBar({ msg: err?.response?.data || 'Failed to store YANG module.' })
    return null
  } finally {
    stopSpinner()
  }
}

const deleteModules = async (ids: number[]): Promise<boolean> => {
  try {
    startSpinner()
    await v2.delete(`${endpoint}/modules`, { data: ids })
    showSnackBar({ msg: 'Module(s) deleted.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to delete module(s).' })
    return false
  } finally {
    stopSpinner()
  }
}

const getModelSets = async (): Promise<YangModelSet[]> => {
  try {
    startSpinner()
    const resp = await v2.get(`${endpoint}/modelsets`)
    return resp.data || []
  } catch (err) {
    showSnackBar({ msg: 'Failed to load model sets.' })
    return []
  } finally {
    stopSpinner()
  }
}

const createModelSet = async (modelSet: YangModelSet): Promise<number | null> => {
  try {
    startSpinner()
    const resp = await v2.post(`${endpoint}/modelsets`, modelSet)
    showSnackBar({ msg: 'Model set created.' })
    return resp.data
  } catch (err: any) {
    showSnackBar({ msg: err?.response?.data || 'Failed to create model set.' })
    return null
  } finally {
    stopSpinner()
  }
}

const updateModelSet = async (id: number, modelSet: YangModelSet): Promise<boolean> => {
  try {
    startSpinner()
    await v2.put(`${endpoint}/modelsets/${id}`, modelSet)
    showSnackBar({ msg: 'Model set updated.' })
    return true
  } catch (err: any) {
    showSnackBar({ msg: err?.response?.data || 'Failed to update model set.' })
    return false
  } finally {
    stopSpinner()
  }
}

const deleteModelSets = async (ids: number[]): Promise<boolean> => {
  try {
    startSpinner()
    await v2.delete(`${endpoint}/modelsets`, { data: ids })
    showSnackBar({ msg: 'Model set(s) deleted.' })
    return true
  } catch (err) {
    showSnackBar({ msg: 'Failed to delete model set(s).' })
    return false
  } finally {
    stopSpinner()
  }
}

export {
  createModelSet,
  createModule,
  deleteModelSets,
  deleteModules,
  getModelSets,
  getModules,
  updateModelSet
}
