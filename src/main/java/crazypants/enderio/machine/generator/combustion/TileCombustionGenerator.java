package crazypants.enderio.machine.generator.combustion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.enderio.core.api.common.util.ITankAccess;
import com.enderio.core.common.fluid.FluidWrapper;
import com.enderio.core.common.util.BlockCoord;

import crazypants.enderio.ModObject;
import crazypants.enderio.fluid.FluidFuelRegister;
import crazypants.enderio.fluid.IFluidCoolant;
import crazypants.enderio.fluid.IFluidFuel;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.SlotDefinition;
import crazypants.enderio.machine.generator.AbstractGeneratorEntity;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.paint.IPaintable;
import crazypants.enderio.power.PowerDistributor;
import crazypants.enderio.tool.SmartTank;
import info.loenwind.autosave.annotations.Storable;
import info.loenwind.autosave.annotations.Store;
import info.loenwind.autosave.annotations.Store.StoreFor;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

@Storable
public class TileCombustionGenerator extends AbstractGeneratorEntity implements IFluidHandler, ITankAccess, IPaintable.IPaintableTileEntity {

  @Store
  private final SmartTank coolantTank = new SmartTank(FluidContainerRegistry.BUCKET_VOLUME * 5);
  @Store
  private final SmartTank fuelTank = new SmartTank(FluidContainerRegistry.BUCKET_VOLUME * 5);
  private boolean tanksDirty;

  @Store({ StoreFor.ITEM, StoreFor.SAVE })
  private int ticksRemaingFuel;
  @Store({ StoreFor.ITEM, StoreFor.SAVE })
  private int ticksRemaingCoolant;
  @Store(StoreFor.CLIENT)
  private boolean active;

  private PowerDistributor powerDis;

  @Store(StoreFor.CLIENT)
  private int generated;

  private boolean inPause = false;

  private boolean generatedDirty = false;

  private int maxOutputTick = 1280;

  private static int IO_MB_TICK = 250;

  private IFluidFuel curFuel;
  private IFluidCoolant curCoolant;

  public TileCombustionGenerator() {
    super(new SlotDefinition(-1, -1, -1, -1, -1, -1), ModObject.blockCombustionGenerator);
  }

  @Override
  protected boolean doPull(@Nullable EnumFacing dir) {
    boolean res = super.doPull(dir);
    if (dir != null && fuelTank.getFluidAmount() < fuelTank.getCapacity()) {
      if (FluidWrapper.transfer(worldObj, getPos().offset(dir), dir.getOpposite(), fuelTank, IO_MB_TICK) > 0) {
        setTanksDirty();
      }
    }
    if (dir != null && coolantTank.getFluidAmount() < coolantTank.getCapacity()) {
      if (FluidWrapper.transfer(worldObj, getPos().offset(dir), dir.getOpposite(), coolantTank, IO_MB_TICK) > 0) {
        setTanksDirty();
        }
    }
    return res;
  }

  @Override
  public boolean supportsMode(@Nullable EnumFacing faceHit, @Nullable IoMode mode) {
    return mode != IoMode.PUSH && mode != IoMode.PUSH_PULL;
  }

  @Override
  public @Nonnull String getMachineName() {
    return ModObject.blockCombustionGenerator.getUnlocalisedName();
  }

  @Override
  protected boolean isMachineItemValidForSlot(int i, ItemStack itemstack) {
    return false;
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public int fill(EnumFacing from, FluidStack resource, boolean doFill) {
    if (resource == null || resource.getFluid() == null || !canFill(from, resource.getFluid())) {
      return 0;
    }
    int res = 0;

    IFluidCoolant cool = FluidFuelRegister.instance.getCoolant(resource.getFluid());
    if (cool != null) {
      res = getCoolantTank().fill(resource, doFill);
    } else {
      IFluidFuel f = FluidFuelRegister.instance.getFuel(resource.getFluid());
      if (f != null) {
        res = getFuelTank().fill(resource, doFill);
      }
    }
    if (res > 0) {
      tanksDirty = true;
    }
    return res;
  }

  @Override
  public FluidStack drain(EnumFacing from, FluidStack resource, boolean doDrain) {
    return null;
  }

  @Override
  public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain) {
    return null;
  }

  @Override
  public void onNeighborBlockChange(Block blockId) {
    super.onNeighborBlockChange(blockId);
    if (powerDis != null) {
      powerDis.neighboursChanged();
    }
  }

  @Override
  protected boolean processTasks(boolean redstoneChecksPassed) {
    boolean res = false;

    if (!redstoneChecksPassed) {
      if (active) {
        active = false;
        res = true;
      }
      return res;
    } else {

      int lastGenerated = generated;

      boolean isActive = generateEnergy();
      if (isActive != active) {
        active = isActive;
        res = true;
      }
      if (lastGenerated != generated) {
        generatedDirty = true;
      }

      if (getEnergyStored() >= getMaxEnergyStored()) {
        inPause = true;
      }

      transmitEnergy();
    }

    if (tanksDirty && shouldDoWorkThisTick(10)) {
      PacketHandler.sendToAllAround(new PacketCombustionTank(this), this);
      tanksDirty = false;
    }

    if (generatedDirty && shouldDoWorkThisTick(10)) {
      generatedDirty = false;
      res = true;
    }

    return res;
  }

  private boolean transmitEnergy() {
    if (getEnergyStored() <= 0) {
      return false;
    }
    if (powerDis == null) {
      powerDis = new PowerDistributor(new BlockCoord(this));
    }
    int transmitted = powerDis.transmitEnergy(worldObj, Math.min(maxOutputTick, getEnergyStored()));
    setEnergyStored(getEnergyStored() - transmitted);
    return transmitted > 0;
  }

  private boolean generateEnergy() {

    generated = 0;

    if ((ticksRemaingCoolant <= 0 && getCoolantTank().getFluidAmount() <= 0) || (ticksRemaingFuel <= 0 && getFuelTank().getFluidAmount() <= 0)
        || getEnergyStored() >= getMaxEnergyStored()) {
      return false;
    }

    // once full, don't start again until we have drained 10 seconds worth of power to prevent
    // flickering on and off constantly when powering a machine that draws less than this produces
    if (inPause) {
      int powerPerCycle = getPowerPerCycle();
      if (getEnergyStored() >= (getMaxEnergyStored() - (powerPerCycle * 200))) {
        return false;
      }
    }
    inPause = false;

    ticksRemaingFuel--;
    if (ticksRemaingFuel <= 0) {
      curFuel = FluidFuelRegister.instance.getFuel(getFuelTank().getFluid());
      if (curFuel == null) {
        return false;
      }
      FluidStack drained = getFuelTank().drain(100, true);
      if (drained == null) {
        return false;
      }
      ticksRemaingFuel = getNumTicksPerMbFuel(curFuel) * drained.amount;

      tanksDirty = true;
    } else if (curFuel == null) {
      curFuel = FluidFuelRegister.instance.getFuel(getFuelTank().getFluid());
      if (curFuel == null) {
        return false;
      }
    }

    ticksRemaingCoolant--;
    if (ticksRemaingCoolant <= 0) {
      updateCoolantFromTank();
      if (curCoolant == null) {
        return false;
      }
      FluidStack drained = getCoolantTank().drain(100, true);
      if (drained == null) {
        return false;
      }
      ticksRemaingCoolant = getNumTicksPerMbCoolant(curCoolant, curFuel) * drained.amount;
    } else if (curCoolant == null) {
      updateCoolantFromTank();
      if (curCoolant == null) {
        return false;
      }
    }

    generated = getPowerPerCycle();
    setEnergyStored(getEnergyStored() + generated);

    return getFuelTank().getFluidAmount() > 0 && getCoolantTank().getFluidAmount() > 0;
  }

  protected void updateCoolantFromTank() {
    curCoolant = FluidFuelRegister.instance.getCoolant(getCoolantTank().getFluid());
  }

  private int getPowerPerCycle() {
    return curFuel == null ? 0 : curFuel.getPowerPerCycle();
  }

  public int getNumTicksPerMbFuel() {
    if (getFuelTank().getFluidAmount() <= 0) {
      return 0;
    }
    return getNumTicksPerMbFuel(FluidFuelRegister.instance.getFuel(getFuelTank().getFluid().getFluid()));
  }

  public int getNumTicksPerMbCoolant() {
    if (getFuelTank().getFluidAmount() <= 0) {
      return 0;
    }
    if (worldObj.isRemote) {
      curFuel = FluidFuelRegister.instance.getFuel(getFuelTank().getFluid());
      updateCoolantFromTank();
    }
    return getNumTicksPerMbCoolant(curCoolant, curFuel);
  }

  static int getNumTicksPerMbFuel(IFluidFuel fuel) {
    if (fuel == null) {
      return 0;
    }
    return fuel.getTotalBurningTime() / 1000;
  }

  public static float HEAT_PER_RF = 0.00023F;

  static int getNumTicksPerMbCoolant(IFluidCoolant coolant, IFluidFuel fuel) {
    if (coolant == null || fuel == null) {
      return 0;
    }
    float power = fuel.getPowerPerCycle();
    float cooling = coolant.getDegreesCoolingPerMB(100);
    double toCool = 1d / (HEAT_PER_RF * power);
    int numTicks = (int) Math.round(toCool / (cooling * 1000));
    return numTicks;
  }

  @Override
  public boolean canFill(EnumFacing from, Fluid fluid) {
    if (isSideDisabled(from) || fluid == null) {
      return false;
    }
    return FluidFuelRegister.instance.getCoolant(fluid) != null || FluidFuelRegister.instance.getFuel(fluid) != null;
  }

  @Override
  public boolean canDrain(EnumFacing from, Fluid fluid) {
    return false;
  }

  @Override
  public FluidTankInfo[] getTankInfo(EnumFacing from) {
    if (isSideDisabled(from)) {
      return new FluidTankInfo[0];
    }
    return new FluidTankInfo[] { getCoolantTank().getInfo(), getFuelTank().getInfo() };
  }

  public int getGeneratedLastTick() {
    if (!active) {
      return 0;
    }
    return generated;
  }

  @Override
  public int getPowerUsePerTick() {
    if (getFuelTank().getFluidAmount() <= 0) {
      return 0;
    }
    IFluidFuel fuel = FluidFuelRegister.instance.getFuel(getFuelTank().getFluid());
    if (fuel == null) {
      return 0;
    }
    return fuel.getPowerPerCycle();
  }

  public FluidTank getCoolantTank() {
    return coolantTank;
  }

  public FluidTank getFuelTank() {
    return fuelTank;
  }

  @Override
  public FluidTank getInputTank(FluidStack forFluidType) {
    if (forFluidType != null) {
      if (FluidFuelRegister.instance.getCoolant(forFluidType.getFluid()) != null) {
        return coolantTank;
      }
      if (FluidFuelRegister.instance.getFuel(forFluidType.getFluid()) != null) {
        return fuelTank;
      }
    }
    return null;
  }

  @Override
  public FluidTank[] getOutputTanks() {
    return new FluidTank[] { /* coolantTank, fuelTank */};
  }

  @Override
  public void setTanksDirty() {
    tanksDirty = true;
  }

}
