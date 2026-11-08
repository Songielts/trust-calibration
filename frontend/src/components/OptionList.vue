<template>
  <div class="options">
    <button
      v-for="(opt, i) in options"
      :key="i"
      type="button"
      class="option"
      :class="{ selected: modelValue === i }"
      :disabled="disabled"
      @click="$emit('update:modelValue', i)"
    >
      <span class="letter">{{ letters[i] }}</span>
      <span>{{ opt }}</span>
      <span v-for="t in tags[i] || []" :key="t.text" class="tag" :class="t.cls">{{ t.text }}</span>
    </button>
  </div>
</template>

<script setup>
defineProps({
  options: { type: Array, required: true },
  modelValue: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
  /** { [optionIndex]: [{ text, cls }] } small labels shown at the end of an option */
  tags: { type: Object, default: () => ({}) }
})
defineEmits(['update:modelValue'])
const letters = ['A', 'B', 'C', 'D', 'E', 'F']
</script>
