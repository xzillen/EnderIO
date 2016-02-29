package crazypants.enderio.conduit.render;

import crazypants.enderio.conduit.ConduitUtil;
import crazypants.enderio.conduit.IConduitBundle;
import crazypants.enderio.render.BlockStateWrapper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;

public class ConduitRenderState extends BlockStateWrapper {
  
  private final IConduitBundle bundle;
  
  private final boolean renderFacade;
  private final boolean renderConduit;
  
  public ConduitRenderState(IBlockState state, IBlockAccess world, BlockPos pos, IConduitBundle bundle) {
    super(state, world, pos);  
    this.bundle = bundle;
    
    EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;

    renderFacade = bundle.hasFacade() && !ConduitUtil.isFacadeHidden(bundle, player);
    renderConduit = !renderFacade || !isFacadeOpaqueCube();
  }

  private boolean isFacadeOpaqueCube() {
    Block b = bundle.getFacadeId();
    if(b != null) {
      return b.isOpaqueCube();
    }
    return false;
  }

  public IConduitBundle getBundle() {
    return bundle;
  }

  public boolean getRenderFacade() {
    return renderFacade;
  }

  public boolean getRenderConduit() {
    return renderConduit;
  }

}