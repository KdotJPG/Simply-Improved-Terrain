# Simply Improved Terrain

Simply Improved Terrain rewrites some of the terrain generation components in Minecraft to improve their visual results, while preserving the overall Vanilla impression. Specifically, it focuses on increasing directional variety, grid independence, and overall pattern variation. This mod is designed as a drop-in with intent for it to be compatible with many modpacks and datapacks. It is also intended to serve as a suggestion to Mojang regarding the technical direction they should take world generation.

### Changes:

- Eliminates stark grid patterns from cliff faces, by splitting the primary terrain formula in `BlendedNoise` into three separate interpolation channels.
- Eliminates grid patterns resulting from flat interpolation of terrain-shaping parameters, by sampling the climate noises every column.
- Shuffles the locations of remaining interpolation-founded terrain creases, by splitting the globally-interval-locked sampling grid into Voronoi-cell patches of randomly-XZ-rotated, randomly-offset grids.
- Enhances angular variety in medium and large scale formations, by injecting [domain-rotation](https://noiseposti.ng/posts/2022-01-16-The-Perlin-Problem-Moving-Past-Square-Noise.html#domain-rotation) into existing unmitigated noise.
  - The "shelf" discontinuity effect from Vanilla is re-implemented in a manner that follows the new internal vertical axis, and also adheres more tightly to a principle of local -- rather than seed-global -- variation by design.
- Improves surface block pattern angular variety by replacing the existing 2D Simplex noise implementation with one that uses a [re-tuned gradient table](https://noiseposti.ng/posts/2022-01-16-The-Perlin-Problem-Moving-Past-Square-Noise.html#algorithms--implementations).
- Revamps End island distribution and merging effects by replacing the current system with [metaball](https://en.wikipedia.org/wiki/Metaballs)-style falloff blending and jittered-grid point sampling.
- Adds visual variety to sand/gravel/clay/ice "disk" deposits by applying statelessly-seedable [OpenSimplex2S](https://github.com/KdotJPG/OpenSimplex2) noise to a radial falloff curve.
- Cleans up interval-locked patterns from final biome border surface patterns through the following retoolings:
  - increasing the `BiomeManager` Voronoi search range its geometrically-correct value,
  - replacing the *closest point wins* model with a *highest aggregated falloff weight wins* model, and
  - sampling the biome map at the `BiomeManager`-displaced positions where possible.
- Addresses visual dissonance in the netherrack patterns below ruined portal structures, by [replacing the Manhattan-distance falloff with a Euclidean-based falloff](https://www.reddit.com/r/minecraftsuggestions/comments/mstkt1/ruined_portals_should_use_euclidean_round_falloff/).

### Hopeful future changes:

- Improvements to the shaping of surface lava pools.
- Improvements to aquifer boundary shaping.
- Improve the random-patched interpolation -- it reduces grid patterns, but I'm still not 100% happy with the way it looks when the terrain noise splitting isn't also applied.
- Per-mod / per-dimension configurability -- I know a lot of you have asked for this!
- Optimize the aggregated-falloff-weight biome border code.
- Figure out why the new sand/gravel/clay/ice "disk" system produces square shapes slightly more often than I would expect.
