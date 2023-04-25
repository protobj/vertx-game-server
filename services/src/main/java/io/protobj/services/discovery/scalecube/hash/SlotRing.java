package io.protobj.services.discovery.scalecube.hash;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SlotRing {
    private static final Logger logger = LoggerFactory.getLogger(SlotRing.class);
    private final String ringName;
    public static final int MAX_SLOT = 16384;

    //hash槽
    private final VirtualNode[] slots = new VirtualNode[MAX_SLOT];
    //判断集群是否填充满
    private final BitSet slotBitSet = new BitSet(MAX_SLOT);
    // 通过startSlot排序，快速找出下一个
    private final SortedArrayList<VirtualNode> virtualNodes = new SortedArrayList<>(Comparator.comparing(VirtualNode::getStartSlot));

    private final Int2ObjectMap<int[]> sid2slotsMap = new Int2ObjectOpenHashMap<>();

    public SlotRing(String ringName) {
        this.ringName = ringName;
    }

    public boolean checkSlots(int sid, int[] slots) {
        for (int i = 0; i < slots.length; i += 2) {
            int start = slots[i];
            int end = slots[i + 1];
            if (start > end || start < 0 || end >= MAX_SLOT) {
                logger.error("slots配置：{}-{},{} {}", ringName, sid, start, end);
                return false;
            }
        }
        return true;
    }

    private final VirtualNode testNode = new VirtualNode(0, 0, 0);

    public boolean addOrUpd(int sid, int[] slots) {
        if (!checkSlots(sid, slots)) {
            return false;
        }
        BitSet newSlotBits = newSlotBits(slots);
        int[] oldSlots = sid2slotsMap.get(sid);
        if (oldSlots != null) {
            if (!Arrays.equals(oldSlots, slots)) {
                BitSet temp = BitSet.valueOf(this.slotBitSet.toLongArray());
                //排除自身所占，查询槽点是否已被占用
                temp.andNot(newSlotBits(oldSlots));
                if (temp.intersects(newSlotBits)) {
                    logger.warn("slots重复 {}", virtualNodes);
                    return false;
                }
                clearSlots(oldSlots);
                removeVirtualNodes(sid, oldSlots);
                if (slots.length == 0) {
                    return false;
                }
                addVirtualNodes(sid, slots);
                fillSlots(newSlotBits);
            }
            return true;
        } else {
            if (slots.length == 0) {
                return false;
            }
            if (slotBitSet.intersects(newSlotBits)) {
                logger.warn("slots重复 {}  {}-{}", virtualNodes, sid, Arrays.toString(slots));
                return false;
            }
            addVirtualNodes(sid, slots);
            fillSlots(newSlotBits);
            if (slotBitSet.cardinality() == MAX_SLOT) {
                logger.info("hash slots full");
            }
            return true;
        }
    }

    private void fillSlots(BitSet newSlotBitSet) {
        slotBitSet.or(newSlotBitSet);
    }

    private void clearSlots(int[] slots) {
        for (int i = 0; i < slots.length; i += 2) {
            slotBitSet.clear(slots[i], slots[i + 1]);
        }
    }

    private void addVirtualNodes(int sid, int[] slots) {
        for (int i = 0; i < slots.length; i += 2) {
            VirtualNode virtualNode = new VirtualNode(slots[i], slots[i + 1], sid);
            virtualNodes.add(virtualNode);
            Arrays.fill(this.slots, virtualNode.getStartSlot(), virtualNode.getEndSlot() + 1, virtualNode);
        }
    }

    private void removeVirtualNodes(int sid, int[] slots) {
        for (int i = 0; i < slots.length; i += 2) {
            virtualNodes.remove(new VirtualNode(i, i + 1, sid));
            Arrays.fill(this.slots, slots[i], slots[i + 1] + 1, null);
        }
    }


    public void delete(int sid) {
        int[] slots = sid2slotsMap.get(sid);
        if (slots == null) {
            return;
        }
        clearSlots(slots);
        addVirtualNodes(sid, slots);
    }

    private BitSet newSlotBits(int[] slots) {
        BitSet slotBits = new BitSet();
        for (int i = 0; i < slots.length; i += 2) {
            slotBits.set(slots[i], slots[i + 1] + 1);
        }
        return slotBits;
    }

    /**
     * @param key
     * @return sid
     */
    public int hit(long key) {
        int slot = JedisClusterCRC16.getSlot(key);
        VirtualNode virtualNode = getVirtualNode(slot);
        if (virtualNode == null) {
            return 0;
        }
        return virtualNode.getSid();
    }

    private VirtualNode getVirtualNode(int slot) {
        VirtualNode virtualNode = slots[slot];
        if (virtualNode == null) {
            testNode.setStartSlot(slot);
            int insertionPoint = virtualNodes.findInsertionPoint(testNode);
            //选服成功
            if (insertionPoint >= virtualNodes.size()) {
                if (slot <= virtualNodes.get(virtualNodes.size() - 1).getEndSlot()) {
                    virtualNode = virtualNodes.get(virtualNodes.size() - 1);
                } else {
                    virtualNode = virtualNodes.get(0);
                }
            } else {
                VirtualNode virtualNode1 = virtualNodes.get(insertionPoint - 1);
                if (slot <= virtualNode1.getEndSlot()) {
                    virtualNode = virtualNodes.get(insertionPoint - 1);
                } else {
                    virtualNode = virtualNodes.get(insertionPoint);
                }
            }
        }
        return virtualNode;
    }
}
