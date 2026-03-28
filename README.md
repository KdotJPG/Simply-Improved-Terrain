# Simply Improved Terrain

Simply Improved Terrain rewrites some of the terrain generation components in Minecraft to improve their visual results, while preserving the overall Vanilla impression. Specifically, it focuses on increasing directional variety, grid independence, and overall shape variation. This mod is designed as a drop-in with intent for it to be compatible with many modpacks and datapacks.

![](https://github.com/KdotJPG/Simply-Improved-Terrain/assets/8829856/aafdf1c8-bc9b-43ea-8fd3-0309de00892c)

### Changes Applied:

- **Sharpens Terrain Curvature**: Reduces square-shaped distortions in the terrain by separating 3D noise into independent interpolation channels and eliminating interpolation from 2D noise.
- **Reduces Angular Bias**: Mitigates the 45° and 90° directional tendencies in larger-scale terrain features by rotating the noise sampling domain.
- **Curbs Global Seed Consequences** Standardizes sampler offsets to maximize per-area variation and eliminate global disparities, while localizing shelf height variation in windswept/shattered terrain.
- **Minimizes Ice and Surface Artifacts**: Softens the 45° bias in ice and surface patterns by expanding the underlying gradient vector table.
- **Cleans Up Blocky Biome Borders** Improves biome transitions by correcting the sample loop range, switching to aggregated falloff blending, and bypassing subsampling.
- **Polishes Terrain Features**: Enhances the natural shapes of terrain features, such as lava lakes and clay deposits, by rounding out spread formulas and introducing falloff noise.
- **Refines End Island Generation**: Sharpens transitions and reduces grid artifacting in the large End island generator by switching to full-resolution metaballs, while small islands receive falloff noise for shape variation.
