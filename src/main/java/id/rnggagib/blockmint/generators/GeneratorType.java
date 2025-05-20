package id.rnggagib.blockmint.generators;

public class GeneratorType {
    
    private final String id;
    private final String name;
    private final String material;
    private final double baseValue;
    private final double valueMultiplier;
    private final int maxLevel;
    private final long generationTime;
    private final double upgradeCostBase;
    private final double upgradeCostMultiplier;
    private final String textureValue;
    
    // Evolution properties
    private final String evolutionPath;
    private final int evolutionRequiredUsage;
    private final double evolutionRequiredResources;
    private final double evolutionCost;
    
    public GeneratorType(
            String id, 
            String name, 
            String material, 
            double baseValue, 
            long generationTime, 
            double valueMultiplier, 
            int maxLevel, 
            double upgradeCostBase, 
            double upgradeCostMultiplier,
            String textureValue,
            String evolutionPath,
            int evolutionRequiredUsage,
            double evolutionRequiredResources,
            double evolutionCost) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.baseValue = baseValue;
        this.generationTime = generationTime;
        this.valueMultiplier = valueMultiplier;
        this.maxLevel = maxLevel;
        this.upgradeCostBase = upgradeCostBase;
        this.upgradeCostMultiplier = upgradeCostMultiplier;
        this.textureValue = textureValue;
        this.evolutionPath = evolutionPath;
        this.evolutionRequiredUsage = evolutionRequiredUsage;
        this.evolutionRequiredResources = evolutionRequiredResources;
        this.evolutionCost = evolutionCost;
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
    
    public double getUpgradeCost(int currentLevel) {
        return upgradeCostBase * Math.pow(upgradeCostMultiplier, currentLevel - 1);
    }
    
    public double getValueAtLevel(int level) {
        return baseValue * Math.pow(valueMultiplier, level - 1);
    }
    
    public boolean hasEvolution() {
        return evolutionPath != null && !evolutionPath.isEmpty();
    }
    
    public String getEvolutionPath() {
        return evolutionPath;
    }
    
    public int getEvolutionRequiredUsage() {
        return evolutionRequiredUsage;
    }
    
    public double getEvolutionRequiredResources() {
        return evolutionRequiredResources;
    }
    
    public double getEvolutionCost() {
        return evolutionCost;
    }
}