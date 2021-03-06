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


public class WALLE extends AdvancedRobot{
    private static double BULLET_POWER = 3;
    private final double CIRCLE = 360;
    private double enemyAbsBearing = 0; //敌方绝对bearing = 我方朝向 + 相对bearing
    private double myAbsBearingForEnemy;
    private boolean scaned = false;
    private double radarSpeed = CIRCLE/8;
    private Enemy preEnemy, curEnemy;
    private boolean isMovingAhead = true;

    private String diffHistory = "";
    private HashMap<String, int[]> historyMap = new HashMap<String, int[]>();

    public void setEnemyAbsBearing(double enemyAbsBearing) {
        this.enemyAbsBearing = enemyAbsBearing;
        this.myAbsBearingForEnemy = reverseAngle(enemyAbsBearing);
    }

    public double getEnemyAbsBearing() {
        return enemyAbsBearing;
    }

    public void setRadarSpeed(double radarSpeed) {
        this.radarSpeed = radarSpeed;
    }

    private void reverseRadar(){
        setRadarSpeed(-radarSpeed);
    }

    @Override
    public void run() {
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        setTurnRadarRight(400);
        move();
        while(true) {
            out.println("radarSpeed" + radarSpeed);
            setTurnRadarRight(radarSpeed);
            double radarHeading = getRadarHeading();
//            if(scaned && !outOfEnemyRange(radarHeading) && outOfEnemyRange(radarHeading + radarSpeed)) {
//                reverseRadar();
//                scaned = false;
//            }

//            ahead(100);
//            turnGunLeft(70);
//            back(100);
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
        return Math.abs( minimizeTurn(radarHeading - getEnemyAbsBearing()) ) > CIRCLE/4;
    }

    @Override
    public void onHitWall(HitWallEvent event) {
//        reverseHeading();
//        move();
    }

    private void turnAsReflect(double inAngle, double mirrorAngle) {
        turnToTargetAngle( doReflect(inAngle, mirrorAngle) );
        setAhead(50);
        execute();
    }

    private double doReflect(double inAngle, double mirrorAngle) {
        if(Math.abs( minimizeTurn(inAngle - mirrorAngle) ) > CIRCLE/4)
            mirrorAngle = reverseAngle(mirrorAngle);
//        out.println(String.format("doReflect\n inAngle: %f, mirrorAngle: %f\n" +
//                "outAngle: %f\n", inAngle, mirrorAngle, Mod(2*mirrorAngle - inAngle)));
        return Mod(2*mirrorAngle - inAngle);
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
        dodgeTheBullet();
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        out.println("onScannedRobot: heading = " + event.getHeading());
        curEnemy = new Enemy(event);
        scaned = true;
        setEnemyAbsBearing(Mod(this.getHeading() + event.getBearing()));
//        reverseRadar();

        track();


//        if(enemyMayFire(event))
//            dodgeTheBullet();
//        else {
//            keepCrossWise();   //与敌方炮口保持横向(垂直)，以便躲避子弹
            //setTurnGunRight(angle);
//        }

        //        aimAndFire();
        if(curEnemy.diff != (char)-1) {
            predictAndFire(event);
//            track();
        }

        preEnemy = new Enemy(event);
    }

    private void track() {
        reverseRadar();
//        double angleForRadarTurn = minimizeTurn(enemyAbsBearing - getRadarHeading()) * 1.2;
//        setTurnRadarRight(angleForRadarTurn);
    }

    private void predictAndFire(ScannedRobotEvent event) {
        record(diffHistory, curEnemy.diff);
        double angleForGunTurn = predictAngle(diffHistory);
        setTurnGunRight(angleForGunTurn);
        if(Math.abs(angleForGunTurn) < 10)
            setFire(BULLET_POWER);
    }

    private double predictAngle(String diffHistory) {
        changePower();

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        Point2D.Double enemyPos = getEnemyPos(myPos, Math.toRadians(getEnemyAbsBearing()), curEnemy.getDistance());
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
        BULLET_POWER = Math.max(Math.min((getEnergy()-10)/10, 1000d / curEnemy.distance), 0.1);
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

    private void aimAndFire() {
        double angleForGunTurn = getAngleForGunTurn();
        setTurnGunRight(angleForGunTurn);
        if(Math.abs(angleForGunTurn) < 10) {
            waitFor(new GunTurnCompleteCondition(this));
            setFire(BULLET_POWER);
        }
    }

    private double getAngleForGunTurn() {
        //out.println("getAngleForGunTurn: adjustAngle: " + getAdjustAngle());
        return minimizeTurn((getEnemyAbsBearing() - this.getGunHeading()) + getAdjustAngle());
    }

    private double getAdjustAngle() {   //正弦定理
        double  bulletAngle = Mod(myAbsBearingForEnemy - curEnemy.getHeading()),    //sin(bulletAngle) 自带正负
                bulletSpeed = Rules.getBulletSpeed(BULLET_POWER),
                enemySpeed = curEnemy.getVelocity();
        double enemyAngle = asin(Math.abs(sin(bulletAngle)) * (enemySpeed / bulletSpeed));

        out.println(String.format("getAdjustAngle: myAbsBearingForEnemy = %f, curEnemy.getHeading = %f\n",
                myAbsBearingForEnemy, curEnemy.getHeading()));

        out.println(String.format("getAdjustAngle: bulletAngle = %f, bulletSpeed = %f, enemySpeed = %f\n" +
                "enemyAngle = %f", bulletAngle, bulletSpeed, enemySpeed, enemyAngle));

        //敌人前进方向转向对准你，所花的角度 bulletAngle <180,则在你右边
        return bulletAngle < 180? enemyAngle: -enemyAngle;
        //return enemyAngle;
    }

    private double asin(double x) {
        return Math.toDegrees(Math.asin(x));
    }

    private double sin(double angle) {
        return Math.sin(Math.toRadians(angle));
    }

    private void keepCrossWise() {
        out.println(String.format("getBearing: %f\n", getEnemyAbsBearing()));
        turnToTargetAngle( Mod(getEnemyAbsBearing() + CIRCLE/4) );
    }

    /**
     * 车轮转到指定角度
     * @param targetAngle 指定角度 [0,360)
     */
    private void turnToTargetAngle(double targetAngle) {
        out.println(String.format("targetAngle: %f, Heading: %f\n", targetAngle, getHeading()));
        setTurnRight(minimizeTurn(targetAngle - getHeading()));
    }

    private void dodgeTheBullet() {
        //setAhead(50);
        move();
    }

    private boolean enemyMayFire(ScannedRobotEvent event) {
        out.println("on enemyMayFire");
        return costEnergyForFire(preEnemy.getEnergy() - event.getEnergy());
    }

    private boolean costEnergyForFire(double changedEnergy) {
        out.println("on costEnergyForFire" + changedEnergy);
        return changedEnergy>=0.1 && changedEnergy<=3;
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

    private double reverseAngle(double angle) {
        return Mod(angle + CIRCLE/2);
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

//        public Enemy() {
//            this("", 0, 0, 0, 0, 0);
//        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getEnergy() {
            return energy;
        }

        public void setEnergy(double energy) {
            this.energy = energy;
        }

        public double getHeading() {
            return heading;
        }

        public void setHeading(double heading) {
            this.heading = heading;
        }

        public double getBearing() {
            return bearing;
        }

        public void setBearing(double bearing) {
            this.bearing = bearing;
        }

        public double getDistance() {
            return distance;
        }

        public void setDistance(double distance) {
            this.distance = distance;
        }

        public double getVelocity() {
            return velocity;
        }

        public void setVelocity(double velocity) {
            this.velocity = velocity;
        }

    }

}
