package crazypants.enderio.machine.farm;

import java.util.ArrayList;
import java.util.List;

import crazypants.enderio.machine.AbstractMachineEntity;
import crazypants.enderio.machine.MachineRenderMapper;
import crazypants.enderio.render.EnumRenderMode;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;

public class FarmingStationRenderMapper extends MachineRenderMapper {

  public FarmingStationRenderMapper() {
    super(null);
  }

  @Override
  protected List<IBlockState> render(IBlockState state, IBlockAccess world, BlockPos pos, TileEntity tileEntity, Block block) {
    List<IBlockState> states = new ArrayList<IBlockState>();
    
    boolean active = ((AbstractMachineEntity) tileEntity).isActive();
    if (MinecraftForgeClient.getRenderLayer() == EnumWorldBlockLayer.TRANSLUCENT) {
      if (active) {
        states.add(block.getDefaultState().withProperty(EnumRenderMode.RENDER, EnumRenderMode.FRONT_ON));
      } else {
        states.add(block.getDefaultState().withProperty(EnumRenderMode.RENDER, EnumRenderMode.FRONT_ON_SOUTH));
      }
    } else {
      states.add(block.getDefaultState().withProperty(EnumRenderMode.RENDER, EnumRenderMode.FRONT));
      renderIO(tileEntity, block, states);
    }

    return states;
  }

}
