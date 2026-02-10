import qupath.lib.roi.ROIs
import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.lib.images.servers.*
import qupath.lib.roi.interfaces.*

// Parameters - adjust these as needed
def numSquares = 10           // Number of random squares to generate
def squareSizeUM = 1000       // Size of each square (in micrometers/um)
def minDistanceUM = 50        // Minimum distance between squares (in um, optional)

// Get the current image data
def imageData = getCurrentImageData()
def server = imageData.getServer()

// Get pixel calibration
def cal = server.getPixelCalibration()
def pixelWidth = cal.getPixelWidthMicrons()

// Convert um to pixels
def squareSize = Math.round(squareSizeUM / pixelWidth) as int
def minDistance = Math.round(minDistanceUM / pixelWidth) as int

// Get image dimensions
def width = server.getWidth()
def height = server.getHeight()

// --- TISSUE SELECTION LOGIC ---
// This looks for ALL existing annotations in your hierarchy
def annotations = imageData.getHierarchy().getAnnotationObjects()

if (annotations.isEmpty()) {
    println "No annotations found! Please draw or detect a tissue area first."
    return
}

def tissueROIs = annotations.collect { it.getROI() }
println "Found ${tissueROIs.size()} annotated regions to use as boundaries."

// Create a random number generator
def random = new Random()
def squares = []
def attempts = 0
def maxAttempts = numSquares * 5000
def createdSquares = 0

while (createdSquares < numSquares && attempts < maxAttempts) {
    // Pick one of the annotated regions at random
    def targetROI = tissueROIs.get(random.nextInt(tissueROIs.size()))
    
    // Get bounds of the specific annotation
    def bX = targetROI.getBoundsX()
    def bY = targetROI.getBoundsY()
    def bW = targetROI.getBoundsWidth()
    def bH = targetROI.getBoundsHeight()
    
    // Skip if the annotation is smaller than the square we want to create
    if (bW < squareSize || bH < squareSize) {
        attempts++
        continue
    }

    // Generate random coordinates within the bounding box of that annotation
    def x = bX + random.nextDouble() * (bW - squareSize)
    def y = bY + random.nextDouble() * (bH - squareSize)
    
    // Create the candidate square ROI
    def roi = ROIs.createRectangleROI(x, y, squareSize, squareSize, ImagePlane.getDefaultPlane())
    
    // Check if the square is FULLY contained within the annotated area
    if (isFullyContained(roi, targetROI)) {
        
        // Check minimum distance from other squares already created
        def validLocation = true
        if (minDistance > 0) {
            for (square in squares) {
                def dx = Math.abs(square[0] - x)
                def dy = Math.abs(square[1] - y)
                if (dx < squareSize + minDistance && dy < squareSize + minDistance) {
                    validLocation = false
                    break
                }
            }
        }
        
        if (validLocation) {
            def annotation = PathObjects.createAnnotationObject(roi)
            annotation.setName("Random_Square_${createdSquares + 1}")
            
            // Add to hierarchy
            imageData.getHierarchy().addObject(annotation)
            squares.add([x, y])
            createdSquares++
            println "Created square ${createdSquares} at (${(int)x}, ${(int)y})"
        }
    }
    attempts++
}

// Finalize
imageData.getHierarchy().resolveHierarchy()
fireHierarchyUpdate()

println "Done! Created ${createdSquares} squares inside your annotated areas."

/**
 * Checks if the candidate square is fully inside the target tissue annotation.
 * It checks the 4 corners and the center.
 */
def isFullyContained(squareROI, tissueROI) {
    double x = squareROI.getBoundsX()
    double y = squareROI.getBoundsY()
    double w = squareROI.getBoundsWidth()
    double h = squareROI.getBoundsHeight()
    
    // Define points to check (Corners + Center)
    def points = [
        [x, y], 
        [x + w, y], 
        [x, y + h], 
        [x + w, y + h], 
        [x + w/2, y + h/2]
    ]
    
    for (p in points) {
        if (!tissueROI.contains(p[0], p[1])) {
            return false
        }
    }
    return true
}
