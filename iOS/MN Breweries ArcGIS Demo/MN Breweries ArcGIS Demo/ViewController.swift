//
//  ViewController.swift
//  MN Breweries ArcGIS Demo
//
//  Created by Caleb Mackey on 9/25/17.
//  Copyright © 2017 Caleb Mackey. All rights reserved.
//

import UIKit
import ArcGIS
import MapKit

// webmap ID
let webmapID = "7e526c751d2149519212db957a193093"
let layerName = "MN_Breweries"

class ViewController: UIViewController, AGSGeoViewTouchDelegate, AGSPopupsViewControllerDelegate {
    @IBOutlet weak var mapView: AGSMapView!
    
    // segmented view for switching basemap
    @IBOutlet weak var basemapToggle: UISegmentedControl!
    
    // class properties
    var map: AGSMap!
    var breweries: AGSFeatureLayer!
    var lastQuery:AGSCancelable!
    var popupsVC:AGSPopupsViewController!
    var routeTask:AGSRouteTask!
    var routeParameters:AGSRouteParameters!
    let srWGS = AGSSpatialReference(wkid: 4326)

    override func viewDidLoad() {
        super.viewDidLoad()
        
        // reference map and mapView
        self.map = AGSMap(url: URL(string: "https://www.arcgis.com/home/webmap/viewer.html?webmap=" + webmapID)!)
        self.mapView.map = self.map
        
        // set touch delegate for identify
        self.mapView.touchDelegate = self
        
        //register as an observer for loadStatus property on map
        self.map.addObserver(self, forKeyPath: "loadStatus", options: .new, context: nil)
    }
    
    // MARK: Wait for map loaded and find breweries layer
    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        
        //update the banner label on main thread
        DispatchQueue.main.async { [weak self] in
            
            if let weakSelf = self {
                //get the string for load status
                let status = weakSelf.map.loadStatus
                
                switch status {
                case .failedToLoad:
                    // alert user the map did not load
                    let alertController = UIAlertController(title: "Loading Error", message:
                        "Could not load the map!", preferredStyle: UIAlertControllerStyle.alert)
                    alertController.addAction(UIAlertAction(title: "Dismiss", style: UIAlertActionStyle.default,handler: nil))
                    weakSelf.present(alertController, animated: true, completion: nil)
                    
                case .loaded:
                    // start location with navigation
                    weakSelf.startLocationDisplay(with: AGSLocationDisplayAutoPanMode.recenter)
                    
                    // find brewery layer
                    for layer in weakSelf.map.operationalLayers as NSArray as! [AGSFeatureLayer]{
                        if (layer.name == layerName){
                            weakSelf.breweries = layer
                        }
                    }
                default:
                    print("map load status: other")
                }
            }
        }
    }
    
    // MARK: zoom to current location when map loads
    func startLocationDisplay(with autoPanMode:AGSLocationDisplayAutoPanMode) {
        self.mapView.locationDisplay.autoPanMode = autoPanMode
        self.mapView.locationDisplay.start { (error:Error?) -> Void in
            if error != nil {
                // add alert to show error
                let alertController = UIAlertController(title: "Location Error", message:
                    "Could not get current location!", preferredStyle: UIAlertControllerStyle.alert)
                alertController.addAction(UIAlertAction(title: "Dismiss", style: UIAlertActionStyle.default,handler: nil))
                
                self.present(alertController, animated: true, completion: nil)
            }
        }
    }
    
    //MARK: - AGSGeoViewTouchDelegate to handle identify on touch
    func geoView(_ geoView: AGSGeoView, didTapAtScreenPoint screenPoint: CGPoint, mapPoint: AGSPoint) {
        if let lastQuery = self.lastQuery{
            lastQuery.cancel()
        }
        
        self.lastQuery = self.mapView.identifyLayer(self.breweries, screenPoint: screenPoint, tolerance: 12, returnPopupsOnly: false, maximumResults: 10) { [weak self] (identifyLayerResult: AGSIdentifyLayerResult) -> Void in
            if let error = identifyLayerResult.error {
                print(error)
            }
            else if let weakSelf = self {
                
                // get popups from touch results
                var popups = [AGSPopup]()
                let geoElements = identifyLayerResult.geoElements
                
                for geoElement in geoElements {
                    let popup = AGSPopup(geoElement: geoElement)
                    popups.append(popup)
                }
            
                if popups.count > 0 {
                    weakSelf.popupsVC = AGSPopupsViewController(popups: popups, containerStyle: .navigationBar)
                    
                    // set custom action button to display directions in apple maps
                    let directionsBtn: UIBarButtonItem = UIBarButtonItem()
                    directionsBtn.title = "Directions"
                    directionsBtn.width = CGFloat(100)
                    directionsBtn.action = #selector(ViewController.getAppleDirections) // call our function
                    directionsBtn.target = weakSelf
                    weakSelf.popupsVC.customActionButton = directionsBtn
                    
                    
                    weakSelf.popupsVC.modalPresentationStyle = .formSheet
                    weakSelf.present(weakSelf.popupsVC, animated: true, completion: nil)
                    weakSelf.popupsVC.delegate = weakSelf
                }
            }
        }
    }
    
    // MARK: Dismiss Popup View Controller
    func popupsViewControllerDidFinishViewingPopups(_ popupsViewController: AGSPopupsViewController) {
        
        //dismiss the popups view controller
        self.dismiss(animated: true, completion:nil)
        
        self.popupsVC = nil
    }

    // MARK: change basemap
    @IBAction func changeBasemap(_ sender: UISegmentedControl) {
        switch sender.selectedSegmentIndex {
        case 0:
            self.map.basemap = AGSBasemap.topographic()
        case 1:
            self.map.basemap = AGSBasemap.imagery()
        default:
            self.map.basemap = AGSBasemap.topographic()
        }
    }
    
    // MARK: function to open directions in Apple Maps
    func getAppleDirections(sender: UIButton) -> Void {
        if self.popupsVC != nil {
            // first get a reference to the feature in the popup view controller
            let feature = self.popupsVC.currentPopup?.geoElement
            
            // get it's geometry and convert to WGS84 to get lat/long
            let destination = AGSGeometryEngine.projectGeometry((feature?.geometry)!, to: self.srWGS!) as? AGSPoint
            
            // create CLLocationCoordinate2D to pass into to map driving options
            let coordinate = CLLocationCoordinate2DMake(destination!.y, destination!.x)
            let mapItem = MKMapItem(placemark: MKPlacemark(coordinate: coordinate, addressDictionary:nil))
            
            // set destination name
            mapItem.name = feature?.attributes["Name"] as! String?
            
            // open in Apple Maps app
            mapItem.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey : MKLaunchOptionsDirectionsModeDriving])
        }
    }
    
    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }


}

