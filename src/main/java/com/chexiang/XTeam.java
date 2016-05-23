package com.chexiang;

import java.awt.geom.Point2D;
import java.util.HashMap;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.GunTurnCompleteCondition;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import static robocode.util.Utils.normalAbsoluteAngleDegrees;
import static robocode.util.Utils.normalRelativeAngleDegrees;


public class XTeam extends AdvancedRobot{
    private static double BULLET_POWER = 3;
    private final double CIRCLE = 360;
    private double enemyAbsBearing = 0; //敌方绝对bearing = 我方朝向 + 相对bearing
    private boolean scaned = false;
    private double radarSpeed = CIRCLE/8;
    private Enemy preEnemy, curEnemy;
    private boolean isMovingAhead = true;

    private String diffHistory = "";
    private HashMap<String, int[]> historyMap = new HashMap<String, int[]>();

    private void reverseRadar(){
        radarSpeed = -radarSpeed;
    }

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        setTurnRadarRight(400);
        move();
        while(true) {
            setTurnRadarRight(radarSpeed);
            double radarHeading = getRadarHeading();
            if(scaned && !outOfEnemyRange(radarHeading) && outOfEnemyRange(radarHeading + radarSpeed)) {
                reverseRadar();
                scaned = false;
            }

            if(getTurnRemaining() == 0) {
                reverseHeading();
                setTurnRight(CIRCLE / 3);
                move();
            }
            execute();
        }
    }

    private boolean outOfEnemyRange(double radarHeading) {
        radarHeading = Mod(radarHeading);
        return Math.abs( minimizeTurn(radarHeading - enemyAbsBearing) ) > CIRCLE/4;
    }

    @Override
    public void onHitWall(HitWallEvent event) {
//        reverseHeading();
//        move();
    }

    @Override
    public void onHitRobot(HitRobotEvent event) {
//        reverseHeading();
//        move();
    }

    private void reverseHeading() {
        isMovingAhead = !isMovingAhead;
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        scan();
//        dodgeTheBullet();
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        out.println("onScannedRobot: heading = " + event.getHeading());
        curEnemy = new Enemy(event);

        scaned = true;
        enemyAbsBearing = Mod(this.getHeading() + event.getBearing());

        track();

        if(curEnemy.diff != (char)-1)
            predictAndFire();

        preEnemy = new Enemy(event);
    }

    private void track() {
        reverseRadar();
//        double angleForRadarTurn = minimizeTurn(enemyAbsBearing - getRadarHeading()) * 1.2;
//        setTurnRadarRight(angleForRadarTurn);
    }

    private void predictAndFire() {
        record(diffHistory, curEnemy.diff);
        double angleForGunTurn = predictAngle(diffHistory);
        setTurnGunRight(angleForGunTurn);
        if(Math.abs(angleForGunTurn) < 10)
            setFire(BULLET_POWER);
    }

    private double predictAngle(String diffHistory) {
        changePower();

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        Point2D.Double enemyPos = getEnemyPos(myPos, Math.toRadians(enemyAbsBearing), curEnemy.distance);
        String pattern = new String(diffHistory);
        for (double d = 0; d < myPos.distance(enemyPos); d += Rules.getBulletSpeed(BULLET_POWER)) {
            char nextStep = predictDiff(pattern);
            curEnemy.decode(nextStep);
            enemyPos = getEnemyPos(enemyPos, Math.toRadians(curEnemy.heading), curEnemy.velocity);
            pattern += nextStep;
        }

        enemyAbsBearing = Math.toDegrees( Math.atan2(enemyPos.x - myPos.x, enemyPos.y - myPos.y) );
        return minimizeTurn(enemyAbsBearing - getGunHeading());
    }

    public void changePower() {
        BULLET_POWER = Math.max(Math.min((getEnergy())/3, 1000d / curEnemy.distance), 0.1);
    }

    private static Point2D.Double getEnemyPos(Point2D.Double p, double angle, double distance) {
        double x = p.x + distance * Math.sin(angle);
        double y = p.y + distance * Math.cos(angle);
        return new Point2D.Double(x, y);
    }

    private char predictDiff(String diffHistory) {
        int historyLen = diffHistory.length();
        int[] cnt = null;
        for(int i=0; i<=historyLen && cnt == null; i++)
            cnt = historyMap.get(diffHistory.substring(i, historyLen));
        if(cnt == null) return curEnemy.diff;

        int ans = 0;
        for(int i=0; i<cnt.length; i++)
            if(cnt[ans] < cnt[i])
                ans = i;
        return (char)ans;
    }

    private void record(String oldHistory, char newDiff) {
        int historyLen = oldHistory.length();
        for(int i=0; i<historyLen; i++){
            int[] cnt = historyMap.get(oldHistory.substring(i, historyLen));
            if(cnt == null)
                historyMap.put(oldHistory, cnt = new int[21*17]);
            cnt[newDiff] ++;
        }
        oldHistory += newDiff;
    }

    private double Mod(double angle){   //取模,规整为[0~360)
        return normalAbsoluteAngleDegrees(angle);
//        double tmp = angle + CIRCLE * 999999;
//        return tmp - ((int)(tmp/CIRCLE))*CIRCLE;
    }

    private double minimizeTurn(double angle){  //[0~360) => [-180~180)
        return normalRelativeAngleDegrees(angle);
//        angle = Mod(angle);
//        return angle>CIRCLE/2? angle-CIRCLE: angle;
    }

    private void move(){
        if(isMovingAhead)
            setAhead(5000000);
        else
            setBack(5000000);
    }

    private class Enemy{
        public String name;
        public double energy;
        public double heading;
        public double bearing;
        public double distance;
        public double velocity;
        public char diff;

        public Enemy(String name, double energy, double heading, double bearing, double distance, double velocity) {
            this.name = name;
            this.energy = energy;
            this.heading = heading;
            this.bearing = bearing;
            this.distance = distance;
            this.velocity = velocity;
            this.diff = encode(heading - (preEnemy!=null? preEnemy.heading: 0), velocity);
        }

        public char encode(double dh, double v) {
            dh = minimizeTurn(dh);
            out.println(String.format("encode: dh = %d, v = %d\n", (int)dh, (int)v));
            if (Math.abs(dh) > 10)
                return (char) -1;
            return (char)(((int)dh + 10)*17 + ((int)v + 8));
        }

        public void decode(char diff) {
            out.println(String.format("decode: dh = %d, v = %d\n", diff / 17 - 10, diff % 17 - 8));
            heading += diff / 17 - 10;
            velocity = diff % 17 - 8;
        }

        public Enemy(ScannedRobotEvent event){
            this(
                    event.getName(),
                    event.getEnergy(),
                    event.getHeading(),
                    event.getBearing(),
                    event.getDistance(),
                    event.getVelocity()
            );
        }
    }
}