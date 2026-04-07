package org.agmas.harpymodloader;

import java.util.Map;
import java.util.Random;

import io.wifi.starrailexpress.game.utils.RoleInstance;

public class RoleWeightedUtil extends WeightedUtil<RoleInstance> {
    public RoleWeightedUtil(Map<RoleInstance, Float> weights, Random random) {
        super(weights, random);
    }

    public RoleWeightedUtil(Map<RoleInstance, Float> weights) {
        super(weights);
    }

}