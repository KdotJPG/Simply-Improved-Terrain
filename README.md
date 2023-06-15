# Simply Improved Terrain

Simply Improved Terrain rewrites some of the terrain generation components in Minecraft to improve their visual results, while preserving the overall Vanilla impression. Specifically, it focuses on increasing directional variety, grid independence, and overall feature variation. This mod is designed as a drop-in with intent for it to be compatible with many modpacks and datapacks.

### Changes:

- Mitigates square corrugations in terrain curvature by splitting 3D-sampled noise into separate interpolation channels, and removing interpolation from 2D-sampled noise.
- Removes 45-90-degree bias from large-scale terrain shapes by applying domain rotation to noise samplers.
- Localizes shelf height variation in windswept/shattered terrain by adding per-cell offsets to the corresponding samplers.
- Rechannels per-seed noise layer interaction characteristics into maximized per-area variation by standardizing sampler offsets.
- Reduces 45-degree artifacts in ice and surface patterns by expanding the underlying gradient vector table.
- Cleans up interval-lock from biome border surface patterns by correcting the sample loop range, switching to aggregated weight falloff, and bypassing subsampling.
- Removes grid effects from the End island generator, and smoothes intersection transitions, by switching to full-resolution metaballs.
- Breaks up monotony in circular sand/gravel/clay/ice deposits by adding falloff noise.
- Rounds out shape distributions of lava lake features by switching to isotropic ellipsoid placement formulas. Also adds subtle variation to barriers.
- Removes cardinal direction bias from spread patterns under ruined portals and basalt columns by moving to Euclidean distance metrics.
