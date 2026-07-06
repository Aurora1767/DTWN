export interface EmergencyPlanDocument {
  id: string
  filename: string
  title: string
  url: string
}

const PUBLIC_BASE = '/data/emergency-plans'

export const EMERGENCY_PLAN_DOCUMENTS: EmergencyPlanDocument[] = [
  {
    id: 'flood-drought',
    filename: '无锡市防汛抗旱应急预案.pdf',
    title: '无锡市防汛抗旱应急预案',
    url: `${PUBLIC_BASE}/${encodeURIComponent('无锡市防汛抗旱应急预案.pdf')}`,
  },
  {
    id: 'water-rescue',
    filename: '无锡市水上搜救应急预案.pdf',
    title: '无锡市水上搜救应急预案',
    url: `${PUBLIC_BASE}/${encodeURIComponent('无锡市水上搜救应急预案.pdf')}`,
  },
]
