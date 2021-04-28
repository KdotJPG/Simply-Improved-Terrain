# Simply Improved Terrain

Simply Improved Terrain rewrites some of the terrain generation components in Minecraft to improve their visual results, while preserving the overall Vanilla impression. Specifically, it focuses on reworking directionally-biased, chunk-biased, or otherwise strongly-artificial-looking features. This mod is designed as a drop-in with intent for it to be compatible with many modpacks and datapacks. It is also intended to serve as a suggestion to Mojang regarding the technical direction they should take world generation.

### Changes Included:

- Replaces trilinear interpolation with conditional noise layer skipping, to make full-resolution noise practical. Also removes problematic high-frequency layers. This eliminates the majority of the grid patterns from the terrain.
- Replaces unmitigated Perlin noise with domain-rotated noise, to remove Perlin's characteristic 45-90-degree bias from the horizontal worldplane. This solves the direction variety problem without requiring as much re-tuning as the canonical solution of using Simplex-type noise.
- Re-implements terrain noise "shelves" in a way that localizes height, accounts for domain rotation, and doesn't require interpolation for smoothing.
- Injects domain-rotation into existing unmitigated Perlin noise, so that other features can also take advantage.
- Replaces the gradient vectors in 2D Simplex noise (used for surface block patterns) with a lattice-symmetric 24-sized set which reduces 45-degree artifacts.
- Replaces End Island generator with full-resolution jittered metaballs, to remove grid patterns and allow intersecting islands to merge more nicely.
- Introduces radius variation to the disk-shaped sand/gravel/clay/ice deposits, to make them more convincing. Also removes the sharp points. Idea credit: Origin Realms.
- Replaces Overworld biome transition smoothing with scattered sampling, to conceal the underlying 4x4-interval grid. Also makes rivers wider to avoid constrictions.
- Removes directional bias from the netherrack patterns below ruined portal structures, by replacing the `|Δx|+|Δz|` falloff with a Euclidean-based falloff.

### Planned future changes:

- Rewrite Overworld biome map generation to remove or reduce large-scale directional bias, while following the same layer-based ruleset.
- Replace octahedral-shaped block deposits (particularly in the Nether) with less directionally-biased alternatives.
- Replace Nether and End biome grid magnifiers with full-resolution samplers.
- Improve the shapes of the isolated lake and lava pool features.

### Images

![2021-04-16_20 27 32](https://user-images.githubusercontent.com/8829856/115096752-73fb7400-9ef4-11eb-8dc9-e6347c963d5e.png)
![2021-04-16_20 22 44](https://user-images.githubusercontent.com/8829856/115096627-fd5e7680-9ef3-11eb-834f-55f6f5c4e8c8.png)
![2021-04-16_20 20 47](https://user-images.githubusercontent.com/8829856/115096624-f9caef80-9ef3-11eb-8fd0-b7b0bf8ce9ac.png)
![2021-04-16_20 24 39](https://user-images.githubusercontent.com/8829856/115096631-ffc0d080-9ef3-11eb-8455-2f71ee012821.png)
![2021-04-16_20 30 55_1080](https://user-images.githubusercontent.com/8829856/115096694-4282a880-9ef4-11eb-8734-7bfa6853adaa.png)
![2021-04-16_20 32 30_1080](https://user-images.githubusercontent.com/8829856/115096698-43b3d580-9ef4-11eb-8daa-1d23c15653ed.png)
![2021-04-16_20 34 26](https://user-images.githubusercontent.com/8829856/115096703-48788980-9ef4-11eb-9456-d29c2596ea9a.png)
![2021-04-16_20 38 43_1080](https://user-images.githubusercontent.com/8829856/115096735-6219d100-9ef4-11eb-8ded-8aa2bfc45607.png)