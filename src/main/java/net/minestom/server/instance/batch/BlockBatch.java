package net.minestom.server.instance.batch;

import net.minestom.server.data.Data;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.CustomBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockBatch implements InstanceBatch {

    private InstanceContainer instance;

    private Map<Chunk, List<BlockData>> data = new HashMap<>();

    public BlockBatch(InstanceContainer instance) {
        this.instance = instance;
    }

    @Override
    public synchronized void setBlockStateId(int x, int y, int z, short blockStateId, Data data) {
        final Chunk chunk = this.instance.getChunkAt(x, z);
        addBlockData(chunk, x, y, z, false, blockStateId, (short) 0, data);
    }

    @Override
    public void setCustomBlock(int x, int y, int z, short customBlockId, Data data) {
        final Chunk chunk = this.instance.getChunkAt(x, z);
        final CustomBlock customBlock = BLOCK_MANAGER.getCustomBlock(customBlockId);
        addBlockData(chunk, x, y, z, true, customBlock.getBlockStateId(), customBlockId, data);
    }

    @Override
    public synchronized void setSeparateBlocks(int x, int y, int z, short blockStateId, short customBlockId, Data data) {
        final Chunk chunk = this.instance.getChunkAt(x, z);
        addBlockData(chunk, x, y, z, true, blockStateId, customBlockId, data);
    }

    private void addBlockData(Chunk chunk, int x, int y, int z, boolean customBlock, short blockStateId, short customBlockId, Data data) {
        List<BlockData> blocksData = this.data.get(chunk);
        if (blocksData == null)
            blocksData = new ArrayList<>();

        BlockData blockData = new BlockData();
        blockData.x = x;
        blockData.y = y;
        blockData.z = z;
        blockData.hasCustomBlock = customBlock;
        blockData.blockStateId = blockStateId;
        blockData.customBlockId = customBlockId;
        blockData.data = data;

        blocksData.add(blockData);

        this.data.put(chunk, blocksData);
    }

    public void flush(Runnable callback) {
        int counter = 0;
        for (Map.Entry<Chunk, List<BlockData>> entry : data.entrySet()) {
            counter++;
            final Chunk chunk = entry.getKey();
            final List<BlockData> dataList = entry.getValue();
            final boolean isLast = counter == data.size();
            batchesPool.execute(() -> {
                synchronized (chunk) {
                    if (!chunk.isLoaded())
                        return;

                    for (BlockData data : dataList) {
                        data.apply(chunk);
                    }

                    // Refresh chunk for viewers
                    chunk.sendChunkUpdate();

                    if (isLast) {
                        if (callback != null)
                            callback.run();
                    }

                }
            });
        }
    }

    private static class BlockData {

        private int x, y, z;
        private boolean hasCustomBlock;
        private short blockStateId;
        private short customBlockId;
        private Data data;

        public void apply(Chunk chunk) {
            if (!hasCustomBlock) {
                chunk.UNSAFE_setBlock(x, y, z, blockStateId, data);
            } else {
                chunk.UNSAFE_setCustomBlock(x, y, z, blockStateId, customBlockId, data);
            }
        }

    }

}
