package com.zerofall.ezstorage.configuration;

import com.gtnewhorizon.gtnhlib.config.Config;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.zerofall.ezstorage.Reference;

@Config(modid = Reference.MOD_ID)
public class EZConfiguration {

    @Config.Comment("Count of items that the basic storage box can hold.")
    @Config.DefaultInt(400)
    @Config.RangeInt(min = 1)
    public static int basicCapacity;

    @Config.Comment("Count of items that the condensed storage box can hold.")
    @Config.DefaultInt(4000)
    @Config.RangeInt(min = 1)
    public static int condensedCapacity;

    @Config.Comment("Count of items that the hyper storage box can hold.")
    @Config.DefaultInt(400000)
    @Config.RangeInt(min = 1)
    public static int hyperCapacity;

    @Config.Comment("The maximum amount of different items that can be stored within one storage core, 0 disables the feature, -1 enables the automatic mode.\n"
        + "This option tries to ensure the NBT data wont get too large wich would normally lead to world corruption (network packages going too large).\n"
        + "It is hightly recommended to install hodgepodge to increase the network package size limit!")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0)
    public static int maxItemTypes;

    @Config.Comment("If enabled, sets the limit of 'maxItemTypes' automatically to a possibly harmless value, depending if Hodgepodge is present or not.\n"
        + "It is hightly recommended to install hodgepodge to increase the network package size limit!")
    @Config.DefaultBoolean(true)
    public static boolean maxItemTypesAutoMode;

    @Config.Comment("Focus the input field when opening a storage GUI.")
    @Config.DefaultBoolean(true)
    public static boolean focusGuiInput;

    @Config.Comment("Enables experimental content that might not be stable enough or has known quirks.")
    @Config.DefaultBoolean(false)
    public static boolean experimentalContent;

    @Config.Comment("Last used sort mode for the storage GUI. Values: AMOUNT, NAME, MOD")
    @Config.DefaultString("AMOUNT")
    public static String guiSortMode;

    @Config.Comment("Last used sort order for the storage GUI. Values: DESCENDING, ASCENDING")
    @Config.DefaultString("DESCENDING")
    public static String guiSortOrder;

    @Config.Comment("Last used search mode for the storage GUI. Values: AUTO, NEI_SYNC, NEI_STANDARD, STANDARD")
    @Config.DefaultString("AUTO")
    public static String guiSearchMode;

    @Config.Comment("Whether the search text is preserved when reopening the storage GUI.")
    @Config.DefaultBoolean(false)
    public static boolean guiSaveSearch;

    @Config.Comment("When enabled, clicking on the '+' button in NEI without shift shows ghost items in the crafting grid, instead of moving items.")
    @Config.DefaultBoolean(true)
    public static boolean neiCraftingGhostOverlay;

    @Config.Comment("The saved search text, only used when guiSaveSearch is true.")
    @Config.DefaultString("")
    public static String guiSearchText;

    @Config.Comment("Max total weight in oz a food storage box accepts. 0 = unlimited. Only restricts insertion; extraction is never limited.")
    @Config.DefaultInt(0)
    @Config.RangeInt(min = 0)
    public static int foodStorageCapOz;

    @Config.Comment("Weight in oz extracted from a food storage box per hopper operation.")
    @Config.DefaultInt(160)
    @Config.RangeInt(min = 1)
    public static int hopperExtractOz;

    @Config.Comment("How decay is handled on extraction: intact = only undecayed weight comes out, rot stays behind (default); proportional = extracted portion carries proportional decay.")
    @Config.DefaultString("intact")
    public static String decayExtractMode;

    @Config.Comment("Allow container foods (bowls, etc.) to move through hoppers/pipes in and out of food storage boxes. Containers are stripped into the box's cache on insert and consumed from it on extract; terminal always allows container foods.")
    @Config.DefaultBoolean(false)
    public static boolean allowHopperContainerFood;

    @Config.Comment("Items that can never be knife-split when inserted into a food storage box past its cap. Format: modid:itemname per entry. Unknown entries are logged and ignored. Sandwiches and salads are always non-splittable.")
    @Config.DefaultStringList({})
    public static String[] foodNoSplitBlacklist;

    @Config.Comment("Per-item extract portion cap overrides in oz. Format: modid:itemname=oz per entry. Unknown entries are logged and ignored. Default caps come from each food's own max weight (sandwich 10, salad 20, soup 24, meal 20, plain 160).")
    @Config.DefaultStringList({})
    public static String[] foodExtractCapOverrides;

    public static void init() {
        try {
            ConfigurationManager.registerConfig(EZConfiguration.class);
        } catch (ConfigException ignore) {}
    }

    public static void save() {
        ConfigurationManager.save(EZConfiguration.class);
    }
}
