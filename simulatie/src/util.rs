
use na::{Vector2, Isometry2, Point2};

use std::f32;

#[macro_export]
macro_rules! dump(
    ($($a:expr),*) => {
        println!(concat!("[", file!(), ":", line!(), "]\n", $("\t", stringify!($a), ": {:?}\n "),*), $($a),*);
    }
);

///(x-a)**2 + (y-b)**2 = r0**2 and (x-c)**2 + (y-d)**2 = r1**2
// pub fn circ_intersect(a: f32, b: f32, r0: f32, c: f32, d: f32, r1: f32) -> (Vector2<f32>, Vector2<f32>) {
//     let Dsq = (c-a).powi(2)+(d-b).powi(2);
//     let D = Dsq.sqrt();
//     let S = (1./4.)*((D+r0+r1)*(D+r0-r1)*(D-r0+r1)*(-D+r0+r1)).sqrt();
//     let P = (r0.powi(2)-r1.powi(2))/(2.*Dsq);
//     let Xc = (a+c)/2. + (c-a)*P;
//     let Yc = (b+d)/2. + (d-b)*P;
//     let Xf = 2.*((b-d)/Dsq)*S;
//     let Yf = 2.*((a-c)/Dsq)*S;
//     let (x1, x2) = (Xc + Xf, Xc - Xf);
//     let (y1, y2) = (Yc + Yf, Yc - Yf);
//     (Vector2::new(x1, y1), Vector2::new(x2, y2))
// }

#[derive(new)]
#[derive(Debug)]
pub struct Circle {
    pub a: f32,
    pub b: f32,
    pub r: f32
}

pub fn circ_intersect(c1: Circle, c2: Circle) -> Option<(Point2<f32>, Point2<f32>)> {
    let (x1, mut y1, x2, mut y2);

    let (mut val1, mut val2, mut test);
    // Calculating distance between circles centers
    let D = ((c1.a - c2.a) * (c1.a - c2.a) + (c1.b - c2.b) * (c1.b - c2.b)).sqrt();
    if ((c1.r + c2.r) >= D) && (D >= (c1.r - c2.r).abs()) {
        // Two circles intersects or tangent
        // Area according to Heron's formula
        //----------------------------------
        let a1 = D + c1.r + c2.r;
        let a2 = D + c1.r - c2.r;
        let a3 = D - c1.r + c2.r;
        let a4 = -D + c1.r + c2.r;
        let area = (a1 * a2 * a3 * a4).sqrt() / 4.;
        // Calculating x axis intersection values
        //---------------------------------------
        val1 = (c1.a + c2.a) / 2. + (c2.a - c1.a) * (c1.r * c1.r - c2.r * c2.r) / (2. * D * D);
        val2 = 2. * (c1.b - c2.b) * area / (D * D);
        x1 = val1 + val2;
        x2 = val1 - val2;
        // Calculating y axis intersection values
        //---------------------------------------
        val1 = (c1.b + c2.b) / 2. + (c2.b - c1.b) * (c1.r * c1.r - c2.r * c2.r) / (2. * D * D);
        val2 = 2. * (c1.a - c2.a) * area / (D * D);
        y1 = val1 - val2;
        y2 = val1 + val2;
        // Intersection points are (x1, y1) and (x2, y2)
        // Because for every x we have two values of y, and the same thing for y,
        // we have to verify that the intersection points as chose are on the
        // circle otherwise we have to swap between the points
        test = ((x1 - c1.a).powi(2) + (y1 - c1.b).powi(2) - c1.r.powi(2)).abs();
        // dump!(test);
        if test > 0.00001 {
            // println!("OWEOEO");
            // point is not on the circle, swap between y1 and y2
            // the value of 0.0000001 is arbitrary chose, smaller values are also OK
            // do not use the value 0 because of computer rounding problems
            let tmp = y1;
            y1 = y2;
            y2 = tmp;
        }
        Some((Point2::new(x1, y1), Point2::new(x2, y2)))
    } else {
        // circles are not intersecting each other
        None
    }
}

pub fn pos_delta(half_distance: f32, dt: f32, lw_speed: f32, rw_speed: f32) -> Isometry2<f32> {
    let delta = lw_speed - rw_speed;
    let total = lw_speed + rw_speed;

    let (dx, dy, dangle) = {
        if delta == 0.0 {
            let speed = lw_speed; //= self.module.right_speed();
            let distance = speed;
            (distance, 0., 0.)
        } else {
            let eff1 = (half_distance*total)/(delta);
            let eff2 = (delta*dt)/(half_distance*2.);

            let dx = eff1 * (eff2).sin();
            let dy = -eff1 * ((eff2).cos() - 1.);
            let dangle = (delta*dt)/(half_distance*2.);

            (dx, dy, dangle)
        }
    };
    Isometry2::new(Vector2::new(dx, dy), dangle)
}

pub fn to_positive_rad(angl:f32) -> f32 {
    if angl < 0. {f32::consts::PI * 2. + angl} else { angl }
}