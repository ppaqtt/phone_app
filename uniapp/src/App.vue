<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
import { useNotesStore } from "@/stores/notes";

onLaunch(() => {
  const notesStore = useNotesStore();
  notesStore.loadFromStorage();
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style></style>
