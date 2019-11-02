package mekanism.common.base;

/**
 * @author BloCamLimb
 */
public enum NBTType {
    ALL_SAVE,
    TILE_UPDATE;

    public boolean isAllSave() {
        return this == ALL_SAVE;
    }

    /**
     * Runtime update data, not save to local
     */
    public boolean isTileUpdate() {
        return this == TILE_UPDATE;
    }
}
