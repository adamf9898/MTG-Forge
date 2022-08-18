package forge.adventure.stage;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.viewport.Viewport;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.BiomeData;
import forge.adventure.data.EnemyData;
import forge.adventure.data.WorldData;
import forge.adventure.scene.*;
import forge.adventure.util.Current;
import forge.adventure.util.SaveFileContent;
import forge.adventure.util.SaveFileData;
import forge.adventure.world.World;
import forge.adventure.world.WorldSave;
import forge.gui.FThreads;
import forge.screens.TransitionScreen;
import forge.sound.SoundEffectType;
import forge.sound.SoundSystem;
import forge.util.MyRandom;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;


/**
 * Stage for the over world. Will handle monster spawns
 */
public class WorldStage extends GameStage implements SaveFileContent {

    private static WorldStage instance=null;
    protected EnemySprite currentMob;
    protected Random rand = MyRandom.getRandom();
    WorldBackground background;
    private float spawnDelay = 0;
    private final float spawnInterval = 4;//todo config
    private PointOfInterestMapSprite collidingPoint;
    protected ArrayList<Pair<Float, EnemySprite>> enemies = new ArrayList<>();
    private final Float dieTimer=20f;//todo config
    private Float globalTimer=0f;

    public WorldStage() {
        super();
        background = new WorldBackground(this);
        addActor(background);
        background.setZIndex(0);
    }

    public static WorldStage getInstance() {
        return instance == null ? instance = new WorldStage() : instance;
    }


    final Rectangle tempBoundingRect=new Rectangle();
    final Vector2 enemyMoveVector =new Vector2();
    @Override
    protected void onActing(float delta) {
        if (player.isMoving()) {
            HandleMonsterSpawn(delta);
            handlePointsOfInterestCollision();
            globalTimer+=delta;
            Iterator<Pair<Float, EnemySprite>> it = enemies.iterator();
            while (it.hasNext()) {
                Pair<Float, EnemySprite> pair= it.next();
                if(globalTimer>=pair.getKey()+dieTimer)
                {

                    foregroundSprites.removeActor(pair.getValue());
                    it.remove();
                    continue;
                }
                EnemySprite mob=pair.getValue();

                enemyMoveVector.set(player.getX(), player.getY()).sub(mob.pos());
                enemyMoveVector.setLength(mob.speed()*delta);
                tempBoundingRect.set(mob.getX()+ enemyMoveVector.x,mob.getY()+ enemyMoveVector.y,mob.getWidth(),mob.getHeight()*mob.getCollisionHeight());

                if(!mob.getData().flying && WorldSave.getCurrentSave().getWorld().collidingTile(tempBoundingRect))//if direct path is not possible
                {
                    tempBoundingRect.set(mob.getX()+ enemyMoveVector.x,mob.getY(),mob.getWidth(),mob.getHeight());
                    if(WorldSave.getCurrentSave().getWorld().collidingTile(tempBoundingRect))//if only x path is not possible
                    {
                        tempBoundingRect.set(mob.getX(),mob.getY()+ enemyMoveVector.y,mob.getWidth(),mob.getHeight());
                        if(!WorldSave.getCurrentSave().getWorld().collidingTile(tempBoundingRect))//if y path is possible
                        {
                            mob.moveBy(0, enemyMoveVector.y);
                        }
                    }
                    else
                    {

                        mob.moveBy(enemyMoveVector.x, 0);
                    }
                }
                else
                {
                    mob.moveBy(enemyMoveVector.x, enemyMoveVector.y);
                }

                if (player.collideWith(mob)) {
                    player.setAnimation(CharacterSprite.AnimationTypes.Attack);
                    mob.setAnimation(CharacterSprite.AnimationTypes.Attack);
                    SoundSystem.instance.play(SoundEffectType.Block, false);
                    Gdx.input.vibrate(50);
                    startPause(0.8f, () -> {
                        Forge.setCursor(null, Forge.magnifyToggle ? "1" : "2");
                        SoundSystem.instance.play(SoundEffectType.ManaBurn, false);
                        DuelScene duelScene = ((DuelScene) SceneType.DuelScene.instance);
                        FThreads.invokeInEdtNowOrLater(() -> {
                            Forge.setTransitionScreen(new TransitionScreen(() -> {
                                duelScene.initDuels(player, mob);
                                Forge.clearTransitionScreen();
                                startPause(0.3f, () -> Forge.switchScene(SceneType.DuelScene.instance));
                            }, Forge.takeScreenshot(), true, false));
                            currentMob = mob;
                            WorldSave.getCurrentSave().autoSave();
                        });
                    });
                    break;
                }
            }


        } else {
            for (Pair<Float, EnemySprite> pair : enemies) {
                pair.getValue().setAnimation(CharacterSprite.AnimationTypes.Idle);
            }
        }
    }
    private void removeEnemy(EnemySprite currentMob) {

        foregroundSprites.removeActor(currentMob);
        Iterator<Pair<Float, EnemySprite>> it = enemies.iterator();
        while (it.hasNext()) {
            Pair<Float, EnemySprite> pair = it.next();
            if (pair.getValue()==currentMob) {
                it.remove();
                return;
            }
        }
    }
    @Override
    public void setWinner(boolean playerIsWinner) {

        if (playerIsWinner) {
            player.setAnimation(CharacterSprite.AnimationTypes.Attack);
            currentMob.setAnimation(CharacterSprite.AnimationTypes.Death);
            startPause(0.5f, new Runnable() {
                @Override
                public void run() {
                    ((RewardScene) SceneType.RewardScene.instance).loadRewards(currentMob.getRewards(), RewardScene.Type.Loot, null);
                    WorldStage.this.removeEnemy(currentMob);
                    currentMob = null;
                    Forge.switchScene(SceneType.RewardScene.instance);
                }
            });
        } else {
            player.setAnimation(CharacterSprite.AnimationTypes.Hit);
            currentMob.setAnimation(CharacterSprite.AnimationTypes.Attack);
            startPause(0.5f, new Runnable() {
                @Override
                public void run() {
                    Current.player().defeated();
                    WorldStage.this.removeEnemy(currentMob);
                    currentMob = null;
                }
            });

        }

    }
    private void handlePointsOfInterestCollision() {

        for (Actor actor : foregroundSprites.getChildren()) {
            if (actor.getClass() == PointOfInterestMapSprite.class) {
                PointOfInterestMapSprite point = (PointOfInterestMapSprite) actor;
                if (player.collideWith(point.getBoundingRect())) {
                    if (point == collidingPoint) {
                        continue;
                    }
                    ((TileMapScene) SceneType.TileMapScene.instance).load(point.getPointOfInterest());
                    Forge.switchScene(SceneType.TileMapScene.instance);
                } else {
                    if (point == collidingPoint) {
                        collidingPoint = null;
                    }
                }
            }

        }
    }
    @Override
    public boolean isColliding(Rectangle boundingRect)
    {

        return WorldSave.getCurrentSave().getWorld().collidingTile(boundingRect);
    }
    public boolean spawn(String enemy)
    {
        return spawn(WorldData.getEnemy(enemy));
    }

    private void HandleMonsterSpawn(float delta) {
        World world = WorldSave.getCurrentSave().getWorld();
        int currentBiome = World.highestBiome(world.getBiome((int) player.getX() / world.getTileSize(), (int) player.getY() / world.getTileSize()));
        List<BiomeData> biomeData = WorldSave.getCurrentSave().getWorld().getData().GetBiomes();
        if (biomeData.size() <= currentBiome) {
            player.setMoveModifier(1.5f);
            return;
        }
        player.setMoveModifier(1.0f);
        BiomeData data = biomeData.get(currentBiome);
        if (data == null) return;

        spawnDelay -= delta;
        if(spawnDelay>=0) return;
        spawnDelay=spawnInterval + ( rand.nextFloat() * 4.0f );

        ArrayList<EnemyData> list = data.getEnemyList();
        if (list == null)
            return;
        EnemyData enemyData = data.getEnemy( 1.0f );
        spawn(enemyData);
    }

    private boolean spawn(EnemyData enemyData) {
        if (enemyData == null)
            return false;
        EnemySprite sprite = new EnemySprite(enemyData);
        float unit = Scene.getIntendedHeight() / 6f;
        Vector2 spawnPos = new Vector2(1, 1);
        for(int j=0;j<10;j++)
        {
            spawnPos.setLength(unit + (unit * 3) * rand.nextFloat());
            spawnPos.setAngleDeg(360 * rand.nextFloat());
            for(int i=0;i<10;i++)
            {
                boolean enemyXIsBigger=sprite.getX()>player.getX();
                boolean enemyYIsBigger=sprite.getY()>player.getY();
                sprite.setX(player.getX() + spawnPos.x+(i*sprite.getWidth()*(enemyXIsBigger?1:-1)));//maybe find a better way to get spawn points
                sprite.setY(player.getY() + spawnPos.y+(i*sprite.getHeight()*(enemyYIsBigger?1:-1)));
                if(sprite.getData().flying || !WorldSave.getCurrentSave().getWorld().collidingTile(sprite.boundingRect()))
                {
                    enemies.add(Pair.of(globalTimer,sprite));
                    foregroundSprites.addActor(sprite);
                    return true;
                }
                int g=0;
            }
        }
        return false;
    }

    @Override
    public void draw() {
        background.setPlayerPos(player.getX(), player.getY());
        //spriteGroup.setCullingArea(new Rectangle(player.getX()-getViewport().getWorldHeight()/2,player.getY()-getViewport().getWorldHeight()/2,getViewport().getWorldHeight(),getViewport().getWorldHeight()));
        super.draw();
        if (WorldSave.getCurrentSave().getPlayer().hasAnnounceFantasy()) {
            MapStage.getInstance().showDeckAwardDialog("{BLINK=WHITE;RED}Chaos Mode!{ENDBLINK}\n"+ WorldSave.getCurrentSave().getPlayer().getName()+ "'s Deck: "+
                    WorldSave.getCurrentSave().getPlayer().getSelectedDeck().getName()+
                    "\nEnemy will use Preconstructed or Random Generated Decks. Genetic AI Decks will be available to some enemies on Hard difficulty.", WorldSave.getCurrentSave().getPlayer().getSelectedDeck());
            WorldSave.getCurrentSave().getPlayer().clearAnnounceFantasy();
        }
    }

    @Override
    public void enter() {

        GetPlayer().LoadPos();
        GetPlayer().setMovementDirection(Vector2.Zero);
        for (Actor actor : foregroundSprites.getChildren()) {
            if (actor.getClass() == PointOfInterestMapSprite.class) {
                PointOfInterestMapSprite point = (PointOfInterestMapSprite) actor;
                if (player.collideWith(point.getBoundingRect())) {
                    collidingPoint = point;
                }
            }

        }
        setBounds(WorldSave.getCurrentSave().getWorld().getWidthInPixels(), WorldSave.getCurrentSave().getWorld().getHeightInPixels());

        GridPoint2 pos = background.translateFromWorldToChunk(player.getX(), player.getY());
        background.loadChunk(pos.x,pos.y);
        handlePointsOfInterestCollision();
    }

    @Override
    public void leave() {
        GetPlayer().storePos();
    }

    @Override
    public void load(SaveFileData data) {
        try {
            clearCache();



            List<Float> timeouts= (List<Float>) data.readObject("timeouts");
            List<String> names  = (List<String>) data.readObject("names");
            List<Float> x       = (List<Float>) data.readObject("x");
            List<Float> y       = (List<Float>) data.readObject("y");
            for(int i=0;i<timeouts.size();i++)
            {
                EnemySprite sprite = new EnemySprite(WorldData.getEnemy(names.get(i)));
                sprite.setX(x.get(i));
                sprite.setY(y.get(i));
                enemies.add(Pair.of(timeouts.get(i),sprite));
                foregroundSprites.addActor(sprite);
            }
            globalTimer=data.readFloat("globalTimer");
        }
        catch (Exception e)
        {

        }
    }

    public void clearCache() {

        for(Pair<Float, EnemySprite> enemy:enemies)
            foregroundSprites.removeActor(enemy.getValue());
        enemies.clear();
        background.clear();
        player=null;
    }

    @Override
    public SaveFileData save() {
        SaveFileData data=new SaveFileData();
        List<Float> timeouts=new ArrayList<>();
        List<String> names=new ArrayList<>();
        List<Float> x=new ArrayList<>();
        List<Float> y=new ArrayList<>();
        for(Pair<Float, EnemySprite> enemy:enemies)
        {
            timeouts.add(enemy.getKey());
            names.add(enemy.getValue().getData().name);
            x.add(enemy.getValue().getX());
            y.add(enemy.getValue().getY());
        }
        data.storeObject("timeouts",timeouts);
        data.storeObject("names",names);
        data.storeObject("x",x);
        data.storeObject("y",y);
        data.store("globalTimer",globalTimer);
        return data;
    }

    @Override
    public Viewport getViewport() {
        return super.getViewport();
    }
}
