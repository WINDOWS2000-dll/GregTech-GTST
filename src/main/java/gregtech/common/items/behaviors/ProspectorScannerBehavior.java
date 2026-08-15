package gregtech.common.items.behaviors;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.mui.sync.prospector.ProspectingSyncHandler;
import gregtech.common.mui.widget.GTTextFieldWidget;
import gregtech.common.mui.widget.prospector.ProspectorMode;
import gregtech.common.mui.widget.prospector.WidgetOreList;
import gregtech.common.mui.widget.prospector.WidgetProspectingMap;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class ProspectorScannerBehavior implements IItemBehaviour, ItemUIFactory {

    private static final long VOLTAGE_FACTOR = 16L;
    private static final int FLUID_PROSPECTION_THRESHOLD = GTValues.HV;

    private final int radius;
    private final int tier;

    public ProspectorScannerBehavior(int radius, int tier) {
        this.radius = radius + 1;
        this.tier = tier;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, @NotNull EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                cycleMode(player, heldItem);
            } else if (checkCanUseScanner(heldItem, true)) {
                MetaItemGuiFactory.open(player, hand);
            } else {
                player.sendMessage(new TextComponentTranslation("behavior.prospector.not_enough_energy"));
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    private void cycleMode(@NotNull EntityPlayer player, ItemStack stack) {
        ProspectorMode nextMode = getMode(stack).next();
        if (nextMode == ProspectorMode.FLUID && this.tier < FLUID_PROSPECTION_THRESHOLD) return;

        setMode(stack, nextMode);
        player.sendStatusMessage(new TextComponentTranslation(nextMode == ProspectorMode.FLUID ?
                "metaitem.prospector.mode.fluid" : "metaitem.prospector.mode.ores"), true);
    }

    @NotNull
    private static ProspectorMode getMode(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("Mode", Constants.NBT.TAG_INT)) {
            return ProspectorMode.VALUES[tag.getInteger("Mode")];
        }
        return ProspectorMode.ORE;
    }

    private static void setMode(ItemStack stack, @NotNull ProspectorMode mode) {
        GTUtility.getOrCreateNbtCompound(stack).setInteger("Mode", mode.ordinal());
    }

    private boolean checkCanUseScanner(ItemStack stack, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;

        long amount = GTValues.V[this.tier] / VOLTAGE_FACTOR;
        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        ProspectorMode mode = getMode(guiData.getUsedItemStack());
        ProspectingSyncHandler syncHandler = new ProspectingSyncHandler(guiData.getHand(), this.tier, this.radius,
                mode);
        panelSyncManager.syncValue("prospecting", syncHandler);

        int mapSize = 16 * (this.radius * 2 - 1);
        int listX = mapSize + 10;
        int listWidth = 332 - listX - 6;

        WidgetOreList oreList = new WidgetOreList(syncHandler, listWidth).pos(listX, 36).size(listWidth, 158);
        GTTextFieldWidget searchField = new GTTextFieldWidget() {

            @Override
            protected void onTextChanged() {
                super.onTextChanged();
                oreList.setSearchQuery(getText());
            }
        };
        searchField.pos(listX, 18)
                .size(listWidth, 16)
                .value(new StringValue(""))
                .setPostFix(I18n.format("terminal.component.searching"));

        return GTGuis.createPanel(getTranslationKey(), 332, 200)
                .child(IKey.lang(getTranslationKey()).asWidget().pos(6, 6))
                .child(new WidgetProspectingMap(syncHandler).pos(6, 18))
                .child(searchField)
                .child(oreList);
    }

    private String getTranslationKey() {
        return String.format("metaitem.prospector.%s.name", GTValues.VN[this.tier].toLowerCase(Locale.ROOT));
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        IItemBehaviour.super.addInformation(itemStack, lines);

        if (this.tier >= FLUID_PROSPECTION_THRESHOLD) {
            lines.add(I18n.format("metaitem.prospector.tooltip.fluids", this.radius));
            lines.add(I18n.format(getMode(itemStack).unlocalizedName));
        } else {
            lines.add(I18n.format("metaitem.prospector.tooltip.ores", this.radius));
        }
    }
}
