
use rand;
use rand::distributions::{Range, IndependentSample};
use itertools::Itertools;
use na::{Vector2, Isometry2, Point2, UnitComplex, Real, Translation2};

use parallelism::{Polymorph, Parallel, AvgParallel, Sample};
use parallelism::consts::*;

use std::ops::{Deref, DerefMut};
use std::fmt::{Display, Formatter, Error};

use util;

pub trait Hardware {
    ///In rad.
    const SCAN_SENSORS: usize;
    const SONAR_DEVIANCE: f32;
    const HALF_WIDTH: f32;
    fn scan(&self, id: usize) -> (f32, Isometry2<f32>);
    fn hits(&self, id: usize) -> bool;
}

pub trait Module {
    fn update<T: Hardware>(&mut self,  hardware: &T) -> (f32, f32);
}

const CRD_PRECISION: f32 = 1./32.;
const ANGL_PRECISION: f32 = 1./64.;

const POS_ERR: f32 = 0.9;
const SCAN_ERR: f32 = 0.9;

fn in_range(a: f32, b: f32, range: f32) -> bool {
    a - range < b && a + range > b
}

fn coord_in_range(crd: f32, crd2: f32) -> bool {
    in_range(crd, crd2, CRD_PRECISION)
}

fn angle_in_range(angl: f32, angl2: f32) -> bool {
    in_range(angl, angl2, ANGL_PRECISION)
}

fn vector_in_range(vector: Vector2<f32>, vector2: Vector2<f32>) -> bool {
    coord_in_range(vector[0], vector2[0]) &&
    coord_in_range(vector[1], vector2[1])
}

fn point_in_range(point: Point2<f32>, point2: Point2<f32>) -> bool {
    vector_in_range(point.coords, point2.coords)
}

fn iso_in_range(iso: Isometry2<f32>, iso2: Isometry2<f32>) -> bool {
    vector_in_range(iso.translation.vector, iso2.translation.vector) &&
    angle_in_range(iso.rotation.angle(), iso2.rotation.angle())
}

#[derive(Clone)]
#[derive(Debug)]
struct Absolute(Isometry2<f32>);
#[derive(Clone)]
#[derive(Debug)]
///(position of center of circle + where the segment points towards,
///  radius,
///  half length of border of segment,
///  offset in radians from point in segment to orientation of sensor,
///  offset in radians from the rotation of the robot to the rotation of the sensor,
///  true=sensor looks inwards, not outwards)
struct CircleSegment(Isometry2<f32>, f32, f32, UnitComplex<f32>, UnitComplex<f32>, bool);

impl CircleSegment {
    fn get_circle(&self) -> util::Circle {
        util::Circle {
            a: self.0.translation.vector[0],
            b: self.0.translation.vector[1],
            r: self.1
        }
    }

    /// Rotation of point relative to the center of the circle
    fn rotation_of(&self, p: Point2<f32>) -> UnitComplex<f32> {
        UnitComplex::new((p[1]-self.0.translation.vector[1]).atan2(p[0]-self.0.translation.vector[0]))
    }

    fn point_on_circle(&self, p: Point2<f32>) -> bool {
        //Not exactly!!
        let y_abs = (self.1.powi(2) - p[0].powi(2)).sqrt();
        coord_in_range(p[1], y_abs) || coord_in_range(p[1], -y_abs)
    }

    fn point_within_angle(&self, point_rotation: UnitComplex<f32>) -> bool {
        // let g = p[1].atan2(p[0]);
        // g > (self.0.rotation - self.2) && g < (self.0.rotation= + self.2)
        point_rotation.rotation_to(&self.0.rotation).angle().abs() < self.2
    }

    fn robot_rotation_at(&self, point_rotation: UnitComplex<f32>) -> UnitComplex<f32> {
        let sensor_rotation = self.3.rotation_to(&point_rotation);
        
        self.4.rotation_to(& if self.5 {
            -sensor_rotation
        } else {
            sensor_rotation
        })
    }

    fn agrees_with_position(&self, pos_iso: &Isometry2<f32>) -> bool {
        let point = Point2::from_coordinates(pos_iso.translation.vector);
        self.point_on_circle(point) &&
        {
            let point_rotation = self.rotation_of(point);
            self.point_within_angle(point_rotation) &&
            angle_in_range(self.robot_rotation_at(point_rotation).angle(), pos_iso.rotation.angle())
        }
    }
}

impl Display for CircleSegment {
    fn fmt(&self, f: &mut Formatter) -> Result<(), Error>{
        let &CircleSegment(iso, r, dev, off1, off2, inw) = self;
        let v = iso.translation.vector;
        write!(f, "CS(({}, {}){}, {}, {}, {}, {}, {}), ",
                v[0], v[1], iso.rotation.angle() / f32::pi(), r, dev, off1.angle() / f32::pi(), off2.angle() / f32::pi(), inw)
    }
}

#[derive(Clone)]
#[derive(Debug)]
enum Position {
    Absolute(Absolute),
    CircleSegment(CircleSegment),
}

impl Deref for Position {
    type Target = Isometry2<f32>;
    fn deref(&self) -> &Self::Target {
        match *self {
            Position::Absolute(Absolute(ref iso)) => iso,
            Position::CircleSegment(CircleSegment(ref iso, _, _, _, _, _)) => iso
        }
    }
}

impl DerefMut for Position {
    fn deref_mut(&mut self) -> &mut Self::Target {
        match *self {
            Position::Absolute(Absolute(ref mut iso)) => iso,
            Position::CircleSegment(CircleSegment(ref mut iso, _, _, _, _, _)) => iso
        }
    }
}

#[derive(Debug)]
struct SurfaceHint {
    iso: Isometry2<f32>,
    distance: f32,
    deviance: f32,
    sample: Sample
}

impl SurfaceHint {
    /// relative_iso: Isometry that describes the route from robot to sensor
    ///               NOT through robot_iso * relative_iso but as separate Vector
    ///               and rotation parts ??
    fn hint_position(&self, distance: f32, relative_iso: &Isometry2<f32>) -> PositionHint {
        // dump!(self.iso.translation.vector, self.iso.rotation.angle() / f32::pi());
        let sensor_to_robot_abs = -relative_iso.translation.vector;
        let sensor_to_robot_rel = relative_iso.rotation.inverse() * sensor_to_robot_abs;
        // dump!(relative_iso.translation.vector, relative_iso.rotation.angle() / f32::pi(), sensor_to_robot_abs, sensor_to_robot_rel);
        let (circ_pos, direction, abs_r, rel_vec, inwards);
        if distance > self.distance {
            circ_pos = self.iso.translation.vector;
            direction = (UnitComplex::from_cos_sin_unchecked(-1.,0.) * self.iso.rotation).angle();
            abs_r = distance - self.distance;
            rel_vec = -sensor_to_robot_rel;
            inwards = true;
        } else if distance < self.distance {
            circ_pos = self.iso.translation.vector;
            direction = self.iso.rotation.angle();
            abs_r = self.distance - distance;
            rel_vec = sensor_to_robot_rel;
            inwards = false;
        } else {
            return PositionHint::new(Position::Absolute(Absolute(self.iso)), &self.sample * SCAN_ERR);
        };
        let base_point = Vector2::new(abs_r, 0.); //Point on the farthest right of circle
        let base_point_trans = base_point + rel_vec; //Where the robot would be if the circle was on the origin(according to this scan)
        let rel_r = base_point_trans.norm();
        let e = base_point_trans[1].atan2(base_point_trans[0]); //Shift in radians
        // dump!(circ_pos, direction / f32::pi(), abs_r, rel_vec, base_point, base_point_trans, rel_r, e / f32::pi());
        let iso = Isometry2::new(circ_pos, direction + e);
        // dump!((e+direction)/f32::pi());
        let circ_segment = CircleSegment(iso, rel_r, self.deviance, UnitComplex::new(e), relative_iso.rotation, inwards);
        PositionHint {
            pos: Position::CircleSegment(circ_segment),
            sample: &self.sample * SCAN_ERR
        }
    }
}

impl Default for SurfaceHint {
    fn default() -> Self {
        Self {
            iso: Isometry2::new(Vector2::new(0.,0.),0.),
            distance: 0.,
            deviance: 0.,
            sample: Default::default()
        }
    }
}

impl Parallel for SurfaceHint {
    fn try_merge(&mut self, other: &Self) -> bool {
        if !iso_in_range(self.iso, other.iso)
            || !coord_in_range(self.distance, other.distance)
            || !angle_in_range(self.deviance, other.deviance) 
        { return false }
        //TODO: Shift position to average of the two merges? Instead of just keeping self's?
        self.sample += &other.sample;
        true
    }
    fn beats(&self, other: &Self) -> bool {
        self.sample.avg() > other.sample.avg() ||
        other.sample.weight() == 0.
    }
}

#[derive(Clone)]
#[derive(Debug)]
#[derive(new)]
struct PositionHint {
    // iso: Isometry2<f32>,
    pos: Position,
    sample: Sample
}

impl PositionHint {
    fn certain(iso: Isometry2<f32>) -> Self {
        Self {
            pos: Position::Absolute(Absolute(iso)),
            sample: Sample::new(1.,1.)
        }
    }
}

impl Default for PositionHint {
    fn default() -> Self {
        Self {
            pos: Position::Absolute(Absolute(Isometry2::new(Vector2::new(0.,0.),0.))),
            sample: Sample::default()
        }
    }
}

impl Deref for PositionHint {
    type Target = Position;
    fn deref(&self) -> &Self::Target {
        &self.pos
    }
}

impl DerefMut for PositionHint {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.pos
    }
}

impl Parallel for PositionHint {
    fn try_merge(&mut self, other: &Self) -> bool {
        match self.pos {
            Position::Absolute(Absolute(iso)) => {
                match other.pos {
                    Position::Absolute(Absolute(iso2)) => {
                        if iso_in_range(iso, iso2) {
                            self.sample += &other.sample;
                            //TODO: Shift position to average of the two merges? Instead of just keeping self's?
                            true
                        } else {
                            false
                        }
                    },
                    _ => false
                }
            }, 
            _ => false
        }
    }
    fn beats(&self, other: &Self) -> bool {
        self.sample.avg() > other.sample.avg() ||
        other.sample.weight() == 0.
    }
}

pub struct EnvModule {
    step_rate: f32,
    speeds: (f32, f32),
    surfaces: Polymorph<SurfaceHint, U90>, //3 scans * 10 position parallels = U30
    pos: Polymorph<PositionHint, U30>
}

impl EnvModule {
    pub fn new(step_rate: f32) -> Self {
        let n = Self { 
            surfaces: Polymorph::default(),
            speeds: (1., 1.),
            pos: Polymorph::new_one(PositionHint::certain(
                Isometry2::new(Vector2::new(0.,0.), 0.)
            )),
            step_rate: step_rate 
        };
        // print_pos(&n.pos);
        n
    }
}

impl Module for EnvModule {
    fn update<T: Hardware>(&mut self,  hardware: &T) -> (f32, f32) {
        println!("scan_front: {}, scan_left: {}, scan_right: {}, hits_front: {}",
                  hardware.scan(0).0, hardware.scan(1).0, hardware.scan(2).0, hardware.hits(0));

        print_surf(&self.surfaces);

        //TODO: Support inf
        let scans = (0..T::SCAN_SENSORS).map(|i| hardware.scan(i)).filter(|&(d, _)| !d.is_infinite()).collect::<Vec<_>>().into_boxed_slice();

        // 1. Guess position

        for ref mut pos_hint in self.pos.iter_mut() {
            // dump!(T::HALF_WIDTH, self.step_rate, self.speeds.0, self.speeds.1);
            // dump!(util::pos_delta(T::HALF_WIDTH, self.step_rate, self.speeds.0, self.speeds.1));
            ****pos_hint *= util::pos_delta(T::HALF_WIDTH, self.step_rate, self.speeds.0, self.speeds.1);
            // pos.transform(util::pos_delta(T::HALF_WIDTH, self.step_rate, self.speeds.0, self.speeds.1));
            pos_hint.sample *= POS_ERR;
        }

        let cached_pos = self.pos.clone();

        for surface_hint in self.surfaces.iter() {
            for &(d, rel_sensor_iso) in scans.iter() {
                self.pos.add(surface_hint.hint_position(d, &rel_sensor_iso));
            }
        }

        // needs to happen AFTER updates to pos from the scans ??
        // but without using the positions yielded from that ??
        // hence the cloning of self.pos above ??
        // Update scan data
        for &(d, rel_sensor_iso) in scans.iter() {
            for pos_hint in cached_pos.iter() {
                match pos_hint.pos {
                    Position::Absolute(Absolute(robot_iso)) => {
                        self.surfaces.add(SurfaceHint {
                            iso: robot_iso * rel_sensor_iso, //sensor position(with orientation)
                            distance: d,
                            deviance: T::SONAR_DEVIANCE,
                            sample: &pos_hint.sample * SCAN_ERR
                        });
                    },
                    _ => {}
                }
            }
        }


        let mut incompletes = Vec::new();
        let mut completes = Vec::new();
        for pos_hint in self.pos.iter() {
            match pos_hint.pos {
                Position::CircleSegment(ref circ_segm) => {
                    incompletes.push((circ_segm, &pos_hint.sample))
                }, 
                Position::Absolute(Absolute(ref iso)) => {
                    completes.push((iso.clone(), pos_hint.sample.clone()))
                }
            }
        }

        //IMPORTANT take the isometry of the sans into account
        
        // Add possible absoulte positions to completes that are supported by at least two incompletes.
        for (i, &(circ_segm0, _)) in incompletes.iter().enumerate() {
            for &(circ_segm1, _) in incompletes[(i+1..)].iter() {
                match util::circ_intersect(circ_segm0.get_circle(), circ_segm1.get_circle()) {
                    Some((p1, p2)) => {
                        let mut ifad = |p| {
                            let gs = (circ_segm0.rotation_of(p), circ_segm1.rotation_of(p));
                            if circ_segm0.point_within_angle(gs.0) && circ_segm1.point_within_angle(gs.1) {
                                let robot_rotations = (circ_segm0.robot_rotation_at(gs.0), circ_segm1.robot_rotation_at(gs.1));
                                if angle_in_range(robot_rotations.0.angle(), robot_rotations.1.angle()) {
                                    let trans = Translation2::from_vector(p.coords);
                                    let iso = Isometry2::from_parts(trans, robot_rotations.0);
                                    // let sample = (sample0) + (sample1);
                                    println!("{} and {} agreed on {}", circ_segm0, circ_segm1, iso);
                                    completes.push((iso, Default::default())) //TODO: Sample angle instead of just first
                                }
                            }
                        };
                        ifad(p1);
                        if !point_in_range(p1, p2) {
                            ifad(p2);
                        }
                    },
                    None => {}
                }
            }
        }

        // TODO: Merge average of incomplete to complete if they agree
        for &mut (compl_iso, ref mut compl_sample) in completes.iter_mut() {
            for &(circ_segm, sample) in incompletes.iter() {
                if circ_segm.agrees_with_position(&compl_iso) {
                    *compl_sample += sample;
                }
            }
        }

        print_pos(&self.pos);
        print!("incompletes: {{");
        for (circ_segm, sample) in incompletes.into_iter() {
            let &CircleSegment(iso, r, dev, off1, off2, inw) = circ_segm;
            let v = iso.translation.vector;
            print!("[({}, {}){}, {}, {}, {}, {}, {}: {}], ",
                v[0], v[1], iso.rotation.angle() / f32::pi(), r, dev, off1.angle() / f32::pi(), off2.angle() / f32::pi(), inw, sample.avg())
            
        }
        println!("}}\n");

        // print!("completes: {{");
        // for &(ref iso, ref sample) in completes.iter() {
        //     let v = iso.translation.vector;
        //     print!("[({}, {}) {}: {}], ", v[0], v[1], iso.rotation.angle() / f32::pi(), sample.avg())
        // }
        // println!("}}");

        completes.sort_unstable_by(|&(_, ref sample), &(_, ref sample2)| sample2.avg().partial_cmp(&sample.avg()).unwrap());

        print!("completes: {{");
        for (iso, sample) in completes.into_iter() {
            let v = iso.translation.vector;
            print!("[({}, {}) {}: {}], ", v[0], v[1], iso.rotation.angle() / f32::pi(), sample.avg())
        }
        println!("}}\n");

        // 3. Determine where to move to

        self.speeds
    }
}

fn print_surf(surf: &Polymorph<SurfaceHint, U90>) {
    print!("surf: {{");
    for &SurfaceHint {
        ref iso, distance, deviance, ref sample
    } in surf.iter() {
        let v = iso.translation.vector;
        print!("[({}, {}){}, {}, {}: {}], ", v[0], v[1], iso.rotation.angle() / f32::pi(), distance, deviance, sample.avg())
    }
    println!("}}\n");
}

fn print_pos(pos: &Polymorph<PositionHint, U30>) {
    print!("pos: {{");
    for ph in pos.iter() {
        match ph.pos {
            Position::Absolute(Absolute(iso)) => {
                let v = iso.translation.vector;
                print!("[({}, {}){}: {}], ", v[0], v[1], iso.rotation.angle() / f32::pi(), ph.sample.avg())
            }
            Position::CircleSegment(CircleSegment(iso, r, dev, off1, off2, inw)) => {
                let v = iso.translation.vector;
                print!("[({}, {}){}, {}, {}, {}, {}, {}: {}], ",
                     v[0], v[1], iso.rotation.angle() / f32::pi(), r, dev, off1.angle() / f32::pi(), off2.angle() / f32::pi(), inw, ph.sample.avg())
            }
        }
    }
    println!("}}\n");
}

pub struct RandModule {
    stage: isize
}

impl RandModule {
    pub fn new() -> Self {
        Self { stage: 0 }
    }
}

impl Module for RandModule {
    fn update<T: Hardware>(&mut self,  hardware: &T) -> (f32, f32) {
        println!("scan_front: {}, scan_left: {}, scan_right: {}, hits_front: {}",
                  hardware.scan(0).0, hardware.scan(1).0, hardware.scan(2).0, hardware.hits(0));
        if self.stage == 0 {
            if hardware.scan(0).0 > 0.017 {
                (1.0, 1.001)
            } else {
                self.stage = if rand::random() {1} else {-1};
                self.update(hardware)
            }
        } else {
            if self.stage.abs() > 101 && self.stage.abs() > Range::new(100,400).ind_sample(&mut rand::thread_rng()) {
                self.stage = 0;
                self.update(hardware)
            } else {
                self.stage += self.stage.signum();
                if self.stage < 0 {
                    (-1., -1.5)
                } else {
                    (-1.5, -1.)
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {

    use super::*;
    use robot::SONAR_DEVIANCE;
    use util::*;

    // #[test]
    fn surface_hint1() {
        let robot_pos = Vector2::new(2.,2.);
        let sensor_rel_pos = Isometry2::new(Vector2::new(0.5, 0.), 0.);
        let surf_hint = SurfaceHint {
            iso: Isometry2::new(Vector2::new(2.5,2.), 0.),
            distance: 5.,
            deviance: SONAR_DEVIANCE,
            sample: Default::default()
        };
        let pos_hint = surf_hint.hint_position(5., &sensor_rel_pos);
        dump!(robot_pos, sensor_rel_pos, surf_hint, pos_hint);
        // Abs(2,2)
    }

    // #[test]
    fn surface_hint2() {
        // let robot_pos = Vector2::new(2.,2.);
        let sensor_rel_pos = Isometry2::new(Vector2::new(0.5, 0.), 0.);
        let surf_hint = SurfaceHint {
            iso: Isometry2::new(Vector2::new(2.5,2.), 0.),
            distance: 5.,
            deviance: SONAR_DEVIANCE,
            sample: Default::default()
        };
        let pos_hint = surf_hint.hint_position(3., &sensor_rel_pos);
        dump!(sensor_rel_pos, surf_hint, pos_hint);
        // r=1.5
    }

    // #[test]
    fn surface_hint3() {
        // let robot_pos = Vector2::new(2.,2.);
        let sensor_rel_pos = Isometry2::new(Vector2::new(0.5, 0.), 0.);
        let surf_hint = SurfaceHint {
            iso: Isometry2::new(Vector2::new(2.5,2.), 0.),
            distance: 5.,
            deviance: SONAR_DEVIANCE,
            sample: Default::default()
        };
        let pos_hint = surf_hint.hint_position(7., &sensor_rel_pos);
        dump!(sensor_rel_pos, surf_hint, pos_hint);
        //r=2.5
    }

    #[test]
    fn surface_hint4() {
        // let robot_pos = Vector2::new(2.,2.);
        let sensor_rel_pos = Isometry2::new(Vector2::new(0.5, 0.5), f32::pi()/2.);
        let surf_hint = SurfaceHint {
            iso: Isometry2::new(Vector2::new(2.5,2.5), 0.),
            distance: 5.,
            deviance: SONAR_DEVIANCE,
            sample: Default::default()
        };
        let pos_hint = surf_hint.hint_position(3., &sensor_rel_pos);
        dump!(sensor_rel_pos, surf_hint, pos_hint);
        //r=1.5811388, 
    }
}