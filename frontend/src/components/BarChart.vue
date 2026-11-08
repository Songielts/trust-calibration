<template>
  <div class="chart">
    <div v-if="title" class="chart-title">{{ title }}</div>
    <div class="legend">
      <span v-for="s in series" :key="s.name" class="legend-item">
        <i :style="{ background: s.color }"></i>{{ s.name }}
      </span>
    </div>
    <svg :viewBox="`0 0 ${width} ${height}`" role="img" :aria-label="title" preserveAspectRatio="xMidYMid meet">
      <!-- gridlines -->
      <g v-for="t in ticks" :key="t">
        <line :x1="padL" :x2="width - padR" :y1="y(t)" :y2="y(t)" class="grid" />
        <text :x="padL - 6" :y="y(t) + 4" text-anchor="end" class="axis">{{ formatTick(t) }}</text>
      </g>
      <!-- reference line, e.g. the agent's real accuracy -->
      <g v-if="reference != null">
        <line :x1="padL" :x2="width - padR" :y1="y(reference)" :y2="y(reference)" class="ref" />
        <text :x="width - padR" :y="y(reference) - 5" text-anchor="end" class="ref-label">{{ referenceLabel }}</text>
      </g>
      <!-- bars -->
      <g v-for="(cat, ci) in categories" :key="cat">
        <g v-for="(s, si) in series" :key="s.name">
          <rect
            v-if="s.values[ci] != null"
            :x="barX(ci, si)"
            :y="y(s.values[ci])"
            :width="barW"
            :height="Math.max(0, y(0) - y(s.values[ci]))"
            :fill="s.color"
            rx="3"
          >
            <title>{{ cat }} · {{ s.name }}: {{ formatValue(s.values[ci]) }}</title>
          </rect>
          <text
            v-if="s.values[ci] != null && showValues"
            :x="barX(ci, si) + barW / 2"
            :y="y(s.values[ci]) - 4"
            text-anchor="middle"
            class="val"
          >{{ formatValue(s.values[ci]) }}</text>
        </g>
        <text :x="groupX(ci) + groupW / 2" :y="height - 10" text-anchor="middle" class="cat">{{ cat }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  categories: { type: Array, required: true },
  /** [{ name, color, values: [number|null per category] }] */
  series: { type: Array, required: true },
  max: { type: Number, default: 1 },
  percent: { type: Boolean, default: true },
  reference: { type: Number, default: null },
  referenceLabel: { type: String, default: '' },
  showValues: { type: Boolean, default: true },
  height: { type: Number, default: 260 }
})

const width = 640
const padL = 44
const padR = 12
const padT = 18
const padB = 30

const plotW = computed(() => width - padL - padR)
const groupW = computed(() => plotW.value / Math.max(1, props.categories.length))
const barW = computed(() => Math.min(34, (groupW.value * 0.75) / Math.max(1, props.series.length)))
const ticks = computed(() => [0, 0.25, 0.5, 0.75, 1].map((f) => f * props.max))

const y = (v) => padT + (1 - Math.min(v, props.max) / props.max) * (props.height - padT - padB)
const groupX = (ci) => padL + ci * groupW.value
const barX = (ci, si) => {
  const used = barW.value * props.series.length
  return groupX(ci) + (groupW.value - used) / 2 + si * barW.value
}
const formatValue = (v) => (props.percent ? `${Math.round(v * 100)}%` : Number(v).toFixed(1))
const formatTick = (v) => (props.percent ? `${Math.round(v * 100)}%` : Number(v).toFixed(props.max <= 10 ? 1 : 0))
</script>

<style scoped>
.chart { width: 100%; }
.chart-title { font-weight: 600; margin-bottom: 4px; }
.legend { display: flex; flex-wrap: wrap; gap: 14px; font-size: 0.8rem; color: var(--ink-soft); margin-bottom: 4px; }
.legend-item { display: inline-flex; align-items: center; gap: 6px; }
.legend-item i { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
svg { width: 100%; height: auto; display: block; }
.grid { stroke: #ebe8e1; stroke-width: 1; }
.axis { font-size: 10px; fill: #8a909c; }
.cat { font-size: 11px; fill: #545c6b; }
.val { font-size: 9.5px; fill: #545c6b; }
.ref { stroke: #b45309; stroke-width: 1.5; stroke-dasharray: 5 4; }
.ref-label { font-size: 10px; fill: #b45309; }
</style>
