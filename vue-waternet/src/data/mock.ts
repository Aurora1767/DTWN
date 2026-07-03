import type {
  DispatchPlan,
  HistoricalSensorRecord,
  NetworkOverview,
  RainfallOverview,
  SensorSnapshot,
  SimulationResult,
  WarningEvent,
  WaterQuantityOverview,
} from '@/types/platform'

export const mockNetwork: NetworkOverview = {
  segments: [
    {
      code: 'Lihe_S1',
      name: '蠡河 S1',
      lengthMeters: 1400,
      widthMeters: 20,
      manningN: 0.03,
      startNodeCode: 'N_LH_TH',
      endNodeCode: 'N_LH_MID',
      coordinates: [
        { lng: 120.229, lat: 31.503 },
        { lng: 120.238, lat: 31.493 },
        { lng: 120.247, lat: 31.482 },
      ],
    },
    {
      code: 'Lihe_S2',
      name: '蠡河 S2',
      lengthMeters: 6400,
      widthMeters: 20,
      manningN: 0.03,
      startNodeCode: 'N_LH_MID',
      endNodeCode: 'N_LH_JH',
      coordinates: [
        { lng: 120.247, lat: 31.482 },
        { lng: 120.265, lat: 31.49 },
        { lng: 120.286, lat: 31.501 },
      ],
    },
    {
      code: 'Jinghang_S2',
      name: '京杭运河 S2',
      lengthMeters: 6100,
      widthMeters: 105,
      manningN: 0.02,
      startNodeCode: 'N_JH_C',
      endNodeCode: 'N_JH_S',
      coordinates: [
        { lng: 120.309, lat: 31.499 },
        { lng: 120.312, lat: 31.482 },
        { lng: 120.317, lat: 31.466 },
      ],
    },
    {
      code: 'Daxigang',
      name: '大溪港',
      lengthMeters: 5200,
      widthMeters: 30,
      manningN: 0.03,
      startNodeCode: 'N_DX_W',
      endNodeCode: 'N_DX_E',
      coordinates: [
        { lng: 120.241, lat: 31.445 },
        { lng: 120.273, lat: 31.445 },
        { lng: 120.303, lat: 31.447 },
      ],
    },
  ],
  nodes: [
    {
      code: 'N_LH_TH',
      name: '蠡河太湖边界',
      type: 'BOUNDARY',
      lng: 120.229,
      lat: 31.503,
      initialWaterLevel: 2.5,
      boundaryType: 'WATER_LEVEL',
    },
    {
      code: 'N_JH_N',
      name: '京杭运河北边界',
      type: 'BOUNDARY',
      lng: 120.301,
      lat: 31.525,
      initialWaterLevel: 2.5,
      boundaryType: 'WATER_LEVEL',
    },
    {
      code: 'N_DX_W',
      name: '大溪港西边界',
      type: 'BOUNDARY',
      lng: 120.241,
      lat: 31.445,
      initialWaterLevel: 2.43,
      boundaryType: 'FLOW',
    },
  ],
  structures: [
    {
      code: 'GATE_LH_01',
      name: '蠡河节制闸',
      type: 'GATE',
      nodeCode: 'N_LH_MID',
      designFlow: 80,
      status: 'OPEN',
      lng: 120.247,
      lat: 31.482,
    },
    {
      code: 'PUMP_DX_01',
      name: '大溪港泵站',
      type: 'PUMP',
      nodeCode: 'N_DX_W',
      designFlow: 45,
      status: 'STANDBY',
      lng: 120.241,
      lat: 31.445,
    },
  ],
}

export const mockStations: SensorSnapshot[] = [
  {
    stationCode: 'ST_TH',
    stationName: '太湖边界站',
    nodeCode: 'N_LH_TH',
    waterLevel: 2.52,
    flow: 18.6,
    velocity: 0.31,
    rainfall: 2.4,
    status: 'NORMAL',
    observedAt: new Date().toISOString(),
  },
  {
    stationCode: 'ST_JH_N',
    stationName: '京杭运河北站',
    nodeCode: 'N_JH_N',
    waterLevel: 2.55,
    flow: 20.0,
    velocity: 0.28,
    rainfall: 2.1,
    status: 'WATCH',
    observedAt: new Date().toISOString(),
  },
  {
    stationCode: 'ST_DX',
    stationName: '大溪港站',
    nodeCode: 'N_DX_W',
    waterLevel: 2.43,
    flow: 14.2,
    velocity: 0.2,
    rainfall: 2.6,
    status: 'NORMAL',
    observedAt: new Date().toISOString(),
  },
]

export const mockWarnings: WarningEvent[] = [
  {
    id: 'W-20260701-001',
    targetCode: 'N_JH_N',
    targetName: '京杭运河北站',
    metric: 'waterLevel',
    value: 2.66,
    threshold: 2.6,
    level: 'WARNING',
    status: 'UNCONFIRMED',
    triggeredAt: new Date().toISOString(),
  },
  {
    id: 'W-20260701-002',
    targetCode: 'Lihe_S2',
    targetName: '蠡河 S2',
    metric: 'velocity',
    value: 0.12,
    threshold: 0.15,
    level: 'WATCH',
    status: 'PROCESSING',
    triggeredAt: new Date().toISOString(),
  },
]

export const mockDispatchPlans: DispatchPlan[] = [
  {
    code: 'PLAN-FLOOD-01',
    name: '太湖水位突涨防洪预案',
    type: '防洪调度',
    triggerCondition: '太湖边界水位超过 2.60 m，且京杭运河北站持续上涨',
    measures: ['蠡河节制闸维持开启', '大溪港泵站提高外排流量', '重点巡查蠡河 S2 低洼岸段'],
    expectedEffect: '降低蠡河 S2 最高水位约 0.06 m，缩短超警持续时间',
    riskLevel: 'WARNING',
    relatedSegments: ['Lihe_S1', 'Lihe_S2', 'Daxigang'],
    updatedAt: new Date().toISOString(),
  },
  {
    code: 'PLAN-WQ-01',
    name: '低流速弱交换改善预案',
    type: '水环境改善',
    triggerCondition: '支河平均流速低于 0.15 m/s 且连续 6 小时无明显交换',
    measures: ['开启大溪港泵站小流量循环', '联动蠡河闸形成短时补水', '监测弱交换河段溶解氧变化'],
    expectedEffect: '提升重点河段平均流速，减少滞留区范围',
    riskLevel: 'WATCH',
    relatedSegments: ['Daxigang', 'Lihe_S2'],
    updatedAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
  },
]

export const mockHistoryRecords: HistoricalSensorRecord[] = Array.from({ length: 18 }, (_, index) => ({
  stationCode: index % 2 === 0 ? 'ST_TH' : 'ST_JH_N',
  stationName: index % 2 === 0 ? '太湖边界站' : '京杭运河北站',
  nodeCode: index % 2 === 0 ? 'N_LH_TH' : 'N_JH_N',
  observedAt: new Date(Date.now() - index * 60 * 60 * 1000).toISOString(),
  waterLevel: Number((2.48 + Math.sin(index / 3) * 0.08).toFixed(3)),
  flow: Number((18 + Math.cos(index / 3) * 2.6).toFixed(2)),
  velocity: Number((0.28 + Math.sin(index / 4) * 0.04).toFixed(3)),
  rainfall: Number((1.2 + Math.max(0, Math.sin(index / 4) * 2.1)).toFixed(2)),
  status: index % 5 === 0 ? 'WATCH' : 'NORMAL',
}))

export const mockSimulation: SimulationResult = {
  runId: 'RUN-DEMO',
  scenarioName: '太湖水位突涨预演',
  status: 'SUCCESS',
  runnerType: 'MOCK',
  startedAt: new Date().toISOString(),
  finishedAt: new Date().toISOString(),
  results: [
    {
      segmentCode: 'Lihe_S1',
      segmentName: '蠡河 S1',
      maxWaterLevel: 2.68,
      maxFlow: 23.1,
      averageVelocity: 0.42,
      series: Array.from({ length: 18 }, (_, index) => ({
        step: index,
        timeSeconds: index * 600,
        waterLevel: Number((2.48 + Math.sin(index / 4) * 0.12 + index * 0.004).toFixed(3)),
        flow: Number((18 + Math.sin(index / 3) * 2.4).toFixed(2)),
        velocity: Number((0.32 + Math.sin(index / 5) * 0.05).toFixed(3)),
        riskLevel: index > 12 ? 'WARNING' : 'NORMAL',
      })),
    },
  ],
}

const mockWaterHistory = (
  points: Array<[string, number, number, number]>,
): WaterQuantityOverview['historyByCode'][string] =>
  points.map(([date, waterLevel, flowRate, rainfall]) => ({
    date,
    waterLevel,
    flowRate,
    rainfall,
  }))

export const mockWaterQuantityOverview: WaterQuantityOverview = {
  status: 'success',
  timestamp: new Date().toISOString(),
  live: false,
  stations: [
    { stationCode: '63202900', stationName: '望亭(大)', waterLevel: 3.58, flowRate: 59.4, observedAt: '2026-07-02' },
    { stationCode: '63202800', stationName: '洛社', waterLevel: 3.57, flowRate: 61.7, observedAt: '2026-07-02' },
    { stationCode: '63203000', stationName: '苏州(枫桥)', waterLevel: 3.47, flowRate: 33.7, observedAt: '2026-07-02' },
    { stationCode: '63201015', stationName: '浒墅关', waterLevel: 3.5, flowRate: 19.9, observedAt: '2026-07-02' },
    { stationCode: '63201200', stationName: '白屈港', waterLevel: 3.54, flowRate: 52.8, observedAt: '2026-07-02' },
    { stationCode: '63102100', stationName: '黄埭桥', waterLevel: 3.54, flowRate: 30.0, observedAt: '2026-07-02' },
    { stationCode: '63102150', stationName: '漕桥(三)', waterLevel: 3.55, flowRate: 31.5, observedAt: '2026-07-02' },
    { stationCode: '63204260', stationName: '张桥', waterLevel: 3.51, flowRate: 33.9, observedAt: '2026-07-02' },
  ],
  historyByCode: {
    '63202900': mockWaterHistory([
      ['2026-06-26', 3.55, 68.0, 2.1],
      ['2026-06-27', 3.55, 70.2, 5.4],
      ['2026-06-30', 3.64, 70.7, 8.2],
      ['2026-07-01', 3.72, 72.3, 12.6],
      ['2026-07-02', 3.58, 59.4, 3.8],
    ]),
    '63202800': mockWaterHistory([
      ['2026-06-26', 3.67, 90.5, 1.8],
      ['2026-06-27', 3.62, 57.1, 4.2],
      ['2026-06-30', 3.69, 42.0, 6.5],
      ['2026-07-01', 3.68, 55.5, 9.1],
      ['2026-07-02', 3.57, 61.7, 2.4],
    ]),
    '63203000': mockWaterHistory([
      ['2026-06-26', 3.46, 60.7, 2.6],
      ['2026-06-27', 3.42, 54.8, 3.9],
      ['2026-06-30', 3.39, 82.2, 7.3],
      ['2026-07-01', 3.63, 43.4, 11.2],
      ['2026-07-02', 3.47, 33.7, 1.5],
    ]),
    '63201015': mockWaterHistory([
      ['2026-06-26', 3.42, 49.7, 1.2],
      ['2026-06-27', 3.43, 41.4, 2.8],
      ['2026-06-30', 3.4, 41.1, 5.6],
      ['2026-07-01', 3.48, 39.5, 8.4],
      ['2026-07-02', 3.5, 19.9, 0.8],
    ]),
    '63201200': mockWaterHistory([
      ['2026-06-26', 3.46, 107.0, 3.1],
      ['2026-06-27', 3.48, 86.2, 6.8],
      ['2026-06-30', 3.48, 92.9, 10.5],
      ['2026-07-01', 3.57, 84.2, 14.2],
      ['2026-07-02', 3.54, 52.8, 2.2],
    ]),
    '63102100': mockWaterHistory([
      ['2026-06-26', 3.53, 62.1, 1.6],
      ['2026-06-27', 3.5, 42.4, 3.4],
      ['2026-06-30', 3.5, 55.1, 6.1],
      ['2026-07-01', 3.57, 49.4, 9.8],
      ['2026-07-02', 3.54, 30.0, 1.9],
    ]),
    '63102150': mockWaterHistory([
      ['2026-06-26', 3.53, 46.9, 1.4],
      ['2026-06-27', 3.52, 36.9, 2.5],
      ['2026-06-30', 3.51, 42.3, 5.2],
      ['2026-07-01', 3.56, 36.0, 8.7],
      ['2026-07-02', 3.55, 31.5, 1.1],
    ]),
    '63204260': mockWaterHistory([
      ['2026-06-26', 3.29, 236.0, 4.5],
      ['2026-06-27', 3.31, 250.0, 8.9],
      ['2026-06-30', 3.38, 140.0, 13.6],
      ['2026-07-01', 3.51, 82.0, 16.4],
      ['2026-07-02', 3.51, 33.9, 2.8],
    ]),
  },
}

export const mockRainfallOverview: RainfallOverview = {
  status: 'mock',
  timestamp: new Date().toISOString(),
  live: true,
  points: Array.from({ length: 24 }, (_, index) => {
    const hour = (new Date().getHours() - 23 + index + 24) % 24
    const upstream = Number((Math.max(0, Math.sin(index / 3) * 2.4 + 1.1)).toFixed(2))
    const downstream = Number((Math.max(0, Math.cos(index / 4) * 2.1 + 1.4)).toFixed(2))
    return {
      time: `${String(hour).padStart(2, '0')}:00`,
      upstream,
      downstream,
      rainfall: Number(((upstream + downstream) / 2).toFixed(2)),
    }
  }),
}
