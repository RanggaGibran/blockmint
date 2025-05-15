package id.rnggagib.blockmint.generators;

public class GeneratorType {
    
    private final String id;
    private final String name;
    private final String material;
    private final double baseValue;
    private final double valueMultiplier;
    private final int maxLevel;
    private final long generationTime;
    private final String textureValue;
    
    public GeneratorType(String id, String name, String material, double baseValue, 
                        double valueMultiplier, int maxLevel, long generationTime, String textureValue) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.baseValue = baseValue;
        this.valueMultiplier = valueMultiplier;
        this.maxLevel = maxLevel;
        this.generationTime = generationTime;
        this.textureValue = textureValue;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getMaterial() {
        return material;
    }
    
    public double getBaseValue() {
        return baseValue;
    }
    
    public double getValueMultiplier() {
        return valueMultiplier;
    }
    
    public int getMaxLevel() {
        return maxLevel;
    }
    
    public long getGenerationTime() {
        return generationTime;
    }
    
    public String getTextureValue() {
        return textureValue;
    }
    
    public double getValueAtLevel(int level) {
        return baseValue * Math.pow(valueMultiplier, level - 1);
    }
    
    public double getUpgradeCost(int currentLevel) {
        if (currentLevel >= maxLevel) {
            return -1;
        }
        return getValueAtLevel(currentLevel + 1) * 5;
    }
}