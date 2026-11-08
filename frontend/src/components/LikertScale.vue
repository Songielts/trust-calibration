<template>
  <div class="likert" role="radiogroup" :aria-label="ariaLabel">
    <div class="points">
      <button
        v-for="n in points"
        :key="n"
        type="button"
        class="point"
        :class="{ selected: modelValue === n }"
        role="radio"
        :aria-checked="modelValue === n"
        @click="$emit('update:modelValue', n)"
      >
        {{ n }}
      </button>
    </div>
    <div class="anchors small muted">
      <span>{{ leftLabel }}</span>
      <span v-if="midLabel">{{ midLabel }}</span>
      <span>{{ rightLabel }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: Number, default: null },
  points: { type: Number, default: 7 },
  leftLabel: { type: String, default: 'Strongly disagree' },
  midLabel: { type: String, default: '' },
  rightLabel: { type: String, default: 'Strongly agree' },
  ariaLabel: { type: String, default: 'Rating scale' }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.likert { max-width: 520px; }
.points { display: flex; gap: 6px; }
.point {
  flex: 1;
  height: 40px;
  border: 1.5px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  font: inherit;
  font-weight: 600;
  color: var(--ink-soft);
  cursor: pointer;
  transition: all 0.15s;
}
.point:hover { border-color: var(--accent); }
.point.selected { background: var(--accent); border-color: var(--accent); color: #fff; }
.anchors { display: flex; justify-content: space-between; margin-top: 6px; gap: 8px; }
</style>
