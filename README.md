# Simply Improved Terrain

![Screenshot](https://user-images.githubusercontent.com/8829856/113992706-b7355300-9821-11eb-8d38-c69038ac9f03.png)

Simply Improved Terrain rewrites some of the terrain generation components in Minecraft to improve their visual results, while preserving the overall Vanilla impression. This mod is designed as a drop-in with intent for it to be compatible with most modpacks and datapacks. It is also intended to serve as a suggestion to Mojang regarding the direction they should take world generation.

Specifically, Simply Improved Terrain focuses on reworking directionally-biased, chunk-biased, or otherwise strongly-artificial-looking features. The changes include the following:
- Replaces trilinear interpolation with conditional noise-layer-skipping, to make full-resolution noise practical. Also removes problematic high-frequency layers.
- Replaces unmitigated Perlin noise with domain-rotated noise, to remove its characteristic 45-90-degree bias from the horizontal worldplane.
- Re-implements terrain noise "shelves" in a way that localizes height, accounts for domain rotation, and doesn't require interpolation for smoothing.
- Replaces End Island noise with full-resolution jittered metaballs, to remove grid patterns and allow intersecting islands to merge more nicely.
- Introduces radius variation to the disk-shaped sand/gravel/clay/ice deposits, to make them more convincing. Also removes the sharp points. Idea credit: Origin Realms.
- Replaces Overworld biome transition smoothing with scattered sampling, to conceal the underlying 4x4-interval grid. Also makes rivers wider to avoid constrictions.
- Replaces the gradient vectors in 2D simplex noise (used for surface block patterns) with a lattice-symmetric 24-sized set which reduces 45-degree artifacts.
- Injects domain-rotation into existing unmitigated Perlin noise, so that other features can also take advantage.

Planned future changes:
- Rewrite Overworld biome map generation to remove or reduce large-scale directional bias, while following the same layer-based ruleset.
- Replace octahedral-shaped block deposits (particularly in the Nether) with less directionally-biased alternatives.
- Remove directional bias from the netherrack patterns in portal ruins, by replacing the square/diamond-shaped falloff with euclidean falloff.
- Replace Nether and End biome grid magnifiers with full-resolution samplers.
- Improve the shapes of the isolated lake and lava pool features.
