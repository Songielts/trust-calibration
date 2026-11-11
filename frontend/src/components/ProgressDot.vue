<template>
    <div class="dots" :aria-label="`${done} of ${total} question completed`">
        <span
            v-for="n in total"
            :key="n"
            class="dot"
            :class="{ done: n <= done , current: n === done + 1 }"
        ></span>
      </div>
</template>
<script setup>
defineProps({
    done: {
        type: Number,
        required: true
    },
    total: {
        type: Number,
        required: true
    }
})
</script>
<style scoped>
.dots {
    display: flex;
    gap: 4px;
    flex-wrap:wrap;
    margin-top: 8px;
}
.dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--line);
}

.dot.done {
    background: var(--accent);
}

.dot.current {
    background: var(--surface);
    outline: 2px solid var(--accent);
    outline-offset: 1px;
}
</style>