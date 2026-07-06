<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import PanelShell from '@/components/PanelShell.vue'
import { dbListTables, dbTableSchema, dbQueryRows, dbInsertRow, dbUpdateRow, dbDeleteRow } from '@/services/api'

const tables = ref<string[]>([])
const activeTable = ref('')
const schema = ref<Array<{ name: string; type: string; pk: number }>>([])
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const loading = ref(false)
const search = ref('')
const page = ref(1)
const pageSize = 50

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const editingRow = ref<Record<string, unknown> | null>(null)
const editingOriginal = ref<Record<string, unknown> | null>(null)
const showAddDialog = ref(false)
const newRow = ref<Record<string, unknown>>({})
const errorMsg = ref('')

async function selectTable(table: string) {
  activeTable.value = table
  page.value = 1
  search.value = ''
  schema.value = await dbTableSchema(table)
  await loadRows()
}

async function loadRows() {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await dbQueryRows(activeTable.value, {
      limit: pageSize,
      offset: (page.value - 1) * pageSize,
      search: search.value || undefined,
    })
    rows.value = result.rows
    total.value = result.total
  } catch (e: any) {
    errorMsg.value = e.message ?? '加载失败'
  } finally {
    loading.value = false
  }
}

function doSearch() {
  page.value = 1
  loadRows()
}

function prevPage() {
  if (page.value > 1) { page.value--; loadRows() }
}
function nextPage() {
  if (page.value < totalPages.value) { page.value++; loadRows() }
}

function getPkColumns() {
  return schema.value.filter(c => c.pk > 0).map(c => c.name)
}

function getRowKeys(row: Record<string, unknown>) {
  const pks = getPkColumns()
  if (pks.length > 0) {
    const keys: Record<string, unknown> = {}
    for (const pk of pks) keys[pk] = row[pk]
    return keys
  }
  return { ...row }
}

function startEdit(row: Record<string, unknown>) {
  editingOriginal.value = { ...row }
  editingRow.value = { ...row }
}

function cancelEdit() {
  editingRow.value = null
  editingOriginal.value = null
}

async function saveEdit() {
  if (!editingRow.value || !editingOriginal.value) return
  errorMsg.value = ''
  const keys = getRowKeys(editingOriginal.value)
  const values: Record<string, unknown> = {}
  for (const col of schema.value) {
    if (editingRow.value[col.name] !== editingOriginal.value[col.name]) {
      values[col.name] = editingRow.value[col.name]
    }
  }
  if (Object.keys(values).length === 0) { cancelEdit(); return }
  try {
    await dbUpdateRow(activeTable.value, keys, values)
    cancelEdit()
    await loadRows()
  } catch (e: any) {
    errorMsg.value = e.message ?? '更新失败'
  }
}

async function deleteRow(row: Record<string, unknown>) {
  if (!confirm('确定删除该行数据？')) return
  errorMsg.value = ''
  try {
    await dbDeleteRow(activeTable.value, getRowKeys(row))
    await loadRows()
  } catch (e: any) {
    errorMsg.value = e.message ?? '删除失败'
  }
}

function openAdd() {
  newRow.value = {}
  for (const col of schema.value) newRow.value[col.name] = ''
  showAddDialog.value = true
}

async function submitAdd() {
  errorMsg.value = ''
  try {
    const cleaned: Record<string, unknown> = {}
    for (const col of schema.value) {
      const v = newRow.value[col.name]
      if (v !== '' && v != null) cleaned[col.name] = v
    }
    await dbInsertRow(activeTable.value, cleaned)
    showAddDialog.value = false
    await loadRows()
  } catch (e: any) {
    errorMsg.value = e.message ?? '插入失败'
  }
}

onMounted(async () => {
  tables.value = await dbListTables()
  if (tables.value.length > 0) {
    await selectTable(tables.value[0])
  }
})
</script>

<template>
  <div class="db-page">
    <aside class="db-sidebar">
      <PanelShell title="数据表" eyebrow="TABLES">
        <div class="db-table-list">
          <button
            v-for="t in tables"
            :key="t"
            type="button"
            class="db-table-item"
            :class="{ active: t === activeTable }"
            @click="selectTable(t)"
          >{{ t }}</button>
        </div>
      </PanelShell>
    </aside>

    <main class="db-main">
      <PanelShell :title="activeTable || '选择表'" eyebrow="DATA">
        <div class="db-toolbar">
          <form class="db-search-form" @submit.prevent="doSearch">
            <input v-model="search" class="db-search-input" placeholder="搜索..." type="text" />
            <button class="db-btn" type="submit">查询</button>
          </form>
          <span class="db-count">共 {{ total }} 条</span>
          <button class="db-btn db-btn-add" type="button" @click="openAdd">新增</button>
        </div>

        <p v-if="errorMsg" class="db-error">{{ errorMsg }}</p>

        <div class="db-table-wrap">
          <table class="db-table">
            <thead>
              <tr>
                <th v-for="col in schema" :key="col.name">{{ col.name }}</th>
                <th class="db-col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td :colspan="schema.length + 1" class="db-loading">加载中...</td></tr>
              <tr v-else-if="rows.length === 0"><td :colspan="schema.length + 1" class="db-empty">暂无数据</td></tr>
              <tr v-for="(row, idx) in rows" :key="idx">
                <template v-if="editingOriginal && getRowKeys(editingOriginal)[schema[0]?.name] === row[schema[0]?.name]">
                  <td v-for="col in schema" :key="col.name">
                    <input v-model="editingRow![col.name]" class="db-cell-input" />
                  </td>
                  <td class="db-col-actions">
                    <button class="db-btn db-btn-save" type="button" @click="saveEdit">保存</button>
                    <button class="db-btn" type="button" @click="cancelEdit">取消</button>
                  </td>
                </template>
                <template v-else>
                  <td v-for="col in schema" :key="col.name" class="db-cell">{{ row[col.name] ?? '' }}</td>
                  <td class="db-col-actions">
                    <button class="db-btn" type="button" @click="startEdit(row)">编辑</button>
                    <button class="db-btn db-btn-del" type="button" @click="deleteRow(row)">删除</button>
                  </td>
                </template>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="db-pagination">
          <button class="db-btn" :disabled="page <= 1" @click="prevPage">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button class="db-btn" :disabled="page >= totalPages" @click="nextPage">下一页</button>
        </div>
      </PanelShell>
    </main>

    <div v-if="showAddDialog" class="db-overlay" @click.self="showAddDialog = false">
      <div class="db-dialog">
        <h3>新增记录 · {{ activeTable }}</h3>
        <div class="db-dialog-body">
          <label v-for="col in schema" :key="col.name" class="db-dialog-field">
            <span>{{ col.name }} <em>{{ col.type }}</em></span>
            <input v-model="newRow[col.name]" type="text" />
          </label>
        </div>
        <div class="db-dialog-actions">
          <button class="db-btn" type="button" @click="showAddDialog = false">取消</button>
          <button class="db-btn db-btn-add" type="button" @click="submitAdd">确认新增</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.db-page {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
  min-height: calc(100vh - 122px);
}

.db-sidebar .panel-shell {
  height: 100%;
}

.db-table-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.db-table-item {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid rgba(0, 242, 255, 0.12);
  background: rgba(20, 66, 98, 0.32);
  color: #bfeeff;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition: background 0.12s;
}

.db-table-item:hover {
  background: rgba(26, 112, 158, 0.48);
}

.db-table-item.active {
  border-color: rgba(0, 242, 255, 0.55);
  background: rgba(12, 143, 196, 0.58);
  color: #ffffff;
}

.db-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.db-search-form {
  display: flex;
  gap: 6px;
}

.db-search-input {
  height: 32px;
  min-width: 180px;
  padding: 0 10px;
  border: 1px solid rgba(78, 183, 255, 0.35);
  background: rgba(14, 38, 58, 0.72);
  color: #ffffff;
  font-size: 12px;
}

.db-search-input::placeholder { color: rgba(160, 216, 239, 0.4); }

.db-count {
  color: #83aeca;
  font-size: 12px;
  margin-left: auto;
}

.db-btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid rgba(78, 183, 255, 0.35);
  background: rgba(14, 38, 58, 0.72);
  color: #d8ecfb;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.12s;
}

.db-btn:hover:not(:disabled) {
  border-color: rgba(0, 242, 255, 0.7);
  background: rgba(0, 90, 130, 0.72);
  color: #ffffff;
}

.db-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.db-btn-add {
  border-color: rgba(47, 255, 168, 0.42);
  background: linear-gradient(180deg, rgba(12, 158, 154, 0.72), rgba(9, 101, 138, 0.72));
  color: #ffffff;
}

.db-btn-save {
  border-color: rgba(47, 255, 168, 0.55);
  color: #7dffb2;
}

.db-btn-del {
  border-color: rgba(255, 120, 120, 0.42);
  color: #ffd6dc;
}

.db-btn-del:hover {
  background: rgba(120, 34, 46, 0.72);
}

.db-error {
  margin: 0 0 8px;
  padding: 6px 10px;
  background: rgba(255, 80, 80, 0.12);
  border: 1px solid rgba(255, 80, 80, 0.3);
  color: #ff9f9f;
  font-size: 12px;
}

.db-table-wrap {
  overflow-x: auto;
  overflow-y: auto;
  max-height: calc(100vh - 310px);
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 242, 255, 0.35) rgba(0, 11, 33, 0.45);
}

.db-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.db-table th,
.db-table td {
  padding: 7px 10px;
  border: 1px solid rgba(0, 242, 255, 0.1);
  white-space: nowrap;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.db-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: rgba(24, 105, 144, 0.72);
  color: #8bdcff;
  font-weight: 700;
  text-align: left;
}

.db-table td {
  background: rgba(19, 62, 90, 0.42);
  color: #d9f7ff;
}

.db-table tr:hover td {
  background: rgba(26, 112, 158, 0.38);
}

.db-col-actions {
  min-width: 110px;
  white-space: nowrap;
}

.db-col-actions .db-btn {
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
  margin-right: 4px;
}

.db-cell-input {
  width: 100%;
  min-width: 60px;
  height: 26px;
  padding: 0 6px;
  border: 1px solid rgba(0, 242, 255, 0.4);
  background: rgba(0, 21, 43, 0.72);
  color: #ffffff;
  font-size: 11px;
}

.db-loading, .db-empty {
  text-align: center;
  padding: 24px 12px;
  color: #83aeca;
}

.db-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 10px;
  color: #9cc7dd;
  font-size: 12px;
}

.db-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  background: rgba(4, 16, 28, 0.78);
}

.db-dialog {
  width: min(560px, calc(100vw - 48px));
  max-height: 80vh;
  overflow: auto;
  padding: 18px 20px;
  border: 1px solid rgba(78, 183, 255, 0.42);
  background: linear-gradient(180deg, rgba(12, 34, 52, 0.98), rgba(8, 24, 38, 0.98));
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.5);
}

.db-dialog h3 {
  margin: 0 0 14px;
  color: #ffffff;
  font-size: 16px;
}

.db-dialog-body {
  display: grid;
  gap: 10px;
}

.db-dialog-field {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #9cc7dd;
}

.db-dialog-field em {
  color: #7ea8bf;
  font-style: normal;
  font-size: 10px;
  margin-left: 4px;
}

.db-dialog-field input {
  height: 34px;
  padding: 0 10px;
  border: 1px solid rgba(78, 183, 255, 0.35);
  background: rgba(14, 38, 58, 0.72);
  color: #ffffff;
  font-size: 12px;
}

.db-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>
